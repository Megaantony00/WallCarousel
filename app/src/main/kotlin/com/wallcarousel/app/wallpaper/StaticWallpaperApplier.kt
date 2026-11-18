// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.wallcarousel.app.data.ImageStore
import com.wallcarousel.app.data.StateRepository
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.model.Orientation
import com.wallcarousel.app.data.model.Variant
import com.wallcarousel.app.data.model.Wallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ApplyResult {
  data class Applied(val target: ApplyTarget, val wallpaperNames: List<String>) : ApplyResult
  data object NoCarousel : ApplyResult
  data object NoWallpapers : ApplyResult
  data object NotSupported : ApplyResult

  /** Another trigger applied a wallpaper moments ago; this run did nothing. */
  data object Skipped : ApplyResult
  data class Failed(val cause: Throwable? = null) : ApplyResult
}

/**
 * Applies the next wallpaper of the carousel assigned to a target. The
 * orientation is read at apply time; static wallpapers do not change when the
 * device is rotated afterwards.
 */
class StaticWallpaperApplier(
  context: Context,
  private val repository: StateRepository,
  private val imageStore: ImageStore,
  private val appScope: CoroutineScope,
) {
  private val appContext = context.applicationContext

  // Two triggers can fire at the same time (for example a daily time and an
  // unlock). Serialising the applies makes the minimum gap check reliable.
  private val mutex = Mutex()

  private data class CacheKey(
    val carouselId: String?,
    val wallpaperId: String,
    val variant: Variant,
    val orientation: Orientation,
    val width: Int,
    val height: Int,
  )

  private data class CacheEntry(val key: CacheKey, val bitmap: Bitmap)

  private val preRendered = mutableMapOf<ApplyTarget, CacheEntry>()
  private val cacheLock = Any()

  fun clearPreRendered() {
    synchronized(cacheLock) {
      preRendered.values.forEach { it.bitmap.recycle() }
      preRendered.clear()
    }
  }

  private fun takePreRendered(target: ApplyTarget, key: CacheKey): Bitmap? =
    synchronized(cacheLock) {
      val entry = preRendered[target] ?: return null
      preRendered.remove(target)
      if (entry.key != key) {
        entry.bitmap.recycle()
        return null
      }
      entry.bitmap
    }

  private fun hasPreRendered(target: ApplyTarget, key: CacheKey): Boolean =
    synchronized(cacheLock) { preRendered[target]?.key == key }

  private fun storePreRendered(target: ApplyTarget, key: CacheKey, bitmap: Bitmap) {
    synchronized(cacheLock) {
      preRendered.put(target, CacheEntry(key, bitmap))?.bitmap?.recycle()
    }
  }

  private fun preRender(
    target: ApplyTarget,
    carouselId: String?,
    candidates: List<Pair<Wallpaper, Variant>>,
    nextIndex: Int,
    orientation: Orientation,
    width: Int,
    height: Int,
  ) {
    if (candidates.isEmpty() || repository.state.value?.preRenderNext != true) return
    val (wallpaper, variant) = candidates[nextIndex % candidates.size]
    val key = CacheKey(carouselId, wallpaper.id, variant, orientation, width, height)
    if (hasPreRendered(target, key)) return
    appScope.launch(Dispatchers.Default) {
      val bitmap = WallpaperRenderer.render(
        file = imageStore.file(variant.fileName),
        variant = variant,
        targetWidth = width,
        targetHeight = height,
      ) ?: return@launch
      if (repository.state.value?.preRenderNext != true) {
        bitmap.recycle()
        return@launch
      }
      storePreRendered(target, key, bitmap)
    }
  }

  suspend fun applyNext(target: ApplyTarget, minimumGapMs: Long = 0L): ApplyResult =
    withContext(Dispatchers.IO) {
      mutex.withLock { applyLocked(target, minimumGapMs) }
    }

  private suspend fun applyLocked(target: ApplyTarget, minimumGapMs: Long): ApplyResult {
    val state = repository.ensureLoaded()
    val manager = WallpaperManager.getInstance(appContext)
    if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
      return ApplyResult.NotSupported
    }

    val targets = when (target) {
      ApplyTarget.BOTH -> listOf(ApplyTarget.HOME, ApplyTarget.LOCK)
      else -> listOf(target)
    }
    val orientation = currentOrientation()
    val applied = mutableListOf<String>()
    var sawCarousel = false
    var skipped = false
    var failure: Throwable? = null

    for (single in targets) {
      val config = state.config(single)
      val carousel = state.carousel(config.carouselId) ?: continue
      sawCarousel = true

      val sinceLast = System.currentTimeMillis() - config.lastAppliedAt
      if (minimumGapMs > 0 && config.lastAppliedAt > 0 && sinceLast in 0 until minimumGapMs) {
        skipped = true
        continue
      }

      val candidates = carousel.wallpaperIds.mapNotNull { id ->
        val wallpaper = state.wallpaper(id) ?: return@mapNotNull null
        val variant = wallpaper.variant(orientation) ?: wallpaper.previewVariant()
          ?: return@mapNotNull null
        wallpaper to variant
      }
      if (candidates.isEmpty()) continue

      val index = ((config.cursor % candidates.size) + candidates.size) % candidates.size
      val (wallpaper, variant) = candidates[index]
      val (screenWidth, screenHeight) = displaySize()
      val cacheKey = CacheKey(
        carouselId = config.carouselId,
        wallpaperId = wallpaper.id,
        variant = variant,
        orientation = orientation,
        width = screenWidth,
        height = screenHeight,
      )
      val bitmap = (if (state.preRenderNext) takePreRendered(single, cacheKey) else null)
        ?: WallpaperRenderer.render(
          file = imageStore.file(variant.fileName),
          variant = variant,
          targetWidth = screenWidth,
          targetHeight = screenHeight,
        )
      if (bitmap == null) {
        failure = IllegalStateException("Unable to render ${variant.fileName}")
        // Skip the unreadable entry so the carousel cannot get stuck on it.
        repository.update { current ->
          val updated = current.config(single).copy(cursor = index + 1)
          current.withConfig(single, updated)
        }
        continue
      }

      var appliedOk = false
      try {
        val flags = if (single == ApplyTarget.LOCK) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        manager.setBitmap(bitmap, null, false, flags)
        applied += wallpaper.displayName()
        repository.update { current ->
          val updated = current.config(single).copy(
            cursor = index + 1,
            lastAppliedAt = System.currentTimeMillis(),
          )
          current.withConfig(single, updated)
        }
        appliedOk = true
      } catch (error: Exception) {
        failure = error
      } finally {
        bitmap.recycle()
      }
      if (appliedOk && state.preRenderNext) {
        preRender(
          target = single,
          carouselId = config.carouselId,
          candidates = candidates,
          nextIndex = index + 1,
          orientation = orientation,
          width = screenWidth,
          height = screenHeight,
        )
      }
    }

    return when {
      applied.isNotEmpty() -> ApplyResult.Applied(target, applied)
      !sawCarousel -> ApplyResult.NoCarousel
      failure != null -> ApplyResult.Failed(failure)
      skipped -> ApplyResult.Skipped
      else -> ApplyResult.NoWallpapers
    }
  }

  private fun currentOrientation(): Orientation {
    val (width, height) = displaySize()
    return if (width > height) Orientation.LANDSCAPE else Orientation.PORTRAIT
  }

  private fun displaySize(): Pair<Int, Int> {
    val metrics = DisplayMetrics()
    val display = appContext.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)
    @Suppress("DEPRECATION")
    display.getRealMetrics(metrics)
    return metrics.widthPixels to metrics.heightPixels
  }
}
