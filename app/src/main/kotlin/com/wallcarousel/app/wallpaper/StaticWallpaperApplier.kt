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

  // Both orientations of the currently applied wallpaper. Rotating the tablet
  // then only needs to set the cached bitmap instead of re-rendering it.
  private val orientationCache = mutableMapOf<ApplyTarget, MutableMap<CacheKey, Bitmap>>()
  private val orientationGeneration = mutableMapOf<ApplyTarget, Long>()

  private fun currentGeneration(target: ApplyTarget): Long =
    synchronized(cacheLock) { orientationGeneration[target] ?: 0L }

  private fun newGeneration(target: ApplyTarget): Long =
    synchronized(cacheLock) {
      val next = (orientationGeneration[target] ?: 0L) + 1
      orientationGeneration[target] = next
      next
    }

  private fun cachedOrientation(target: ApplyTarget, key: CacheKey): Bitmap? =
    synchronized(cacheLock) { orientationCache[target]?.get(key) }

  private fun storeOrientation(
    target: ApplyTarget,
    key: CacheKey,
    bitmap: Bitmap,
    generation: Long,
  ) {
    synchronized(cacheLock) {
      if ((orientationGeneration[target] ?: 0L) != generation) {
        bitmap.recycle()
        return
      }
      val map = orientationCache.getOrPut(target) { mutableMapOf() }
      if (map.containsKey(key)) {
        bitmap.recycle()
        return
      }
      map[key] = bitmap
    }
  }

  private fun clearOrientation(target: ApplyTarget) {
    synchronized(cacheLock) {
      orientationCache.remove(target)?.values?.forEach { it.recycle() }
    }
  }

  private fun preRenderOtherOrientation(
    target: ApplyTarget,
    carouselId: String?,
    wallpaper: Wallpaper,
    orientation: Orientation,
    width: Int,
    height: Int,
    generation: Long,
  ) {
    val otherOrientation = if (orientation == Orientation.PORTRAIT) {
      Orientation.LANDSCAPE
    } else {
      Orientation.PORTRAIT
    }
    val otherVariant = wallpaper.variant(otherOrientation) ?: return
    val otherWidth = height
    val otherHeight = width
    val key = CacheKey(carouselId, wallpaper.id, otherVariant, otherOrientation, otherWidth, otherHeight)
    if (cachedOrientation(target, key) != null) return
    appScope.launch(Dispatchers.Default) {
      val otherBitmap = WallpaperRenderer.render(
        file = imageStore.file(otherVariant.fileName),
        variant = otherVariant,
        targetWidth = otherWidth,
        targetHeight = otherHeight,
      ) ?: return@launch
      storeOrientation(target, key, otherBitmap, generation)
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
      var cached = false
      try {
        val flags = if (single == ApplyTarget.LOCK) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        manager.setBitmap(bitmap, null, false, flags)
        applied += wallpaper.displayName()
        repository.update { current ->
          val updated = current.config(single).copy(
            cursor = index + 1,
            lastAppliedAt = System.currentTimeMillis(),
            lastWallpaperId = wallpaper.id,
            lastOrientation = orientation,
          )
          current.withConfig(single, updated)
        }
        val cacheGeneration = newGeneration(single)
        clearOrientation(single)
        storeOrientation(single, cacheKey, bitmap, cacheGeneration)
        cached = true
        preRenderOtherOrientation(
          target = single,
          carouselId = config.carouselId,
          wallpaper = wallpaper,
          orientation = orientation,
          width = screenWidth,
          height = screenHeight,
          generation = cacheGeneration,
        )
        appliedOk = true
      } catch (error: Exception) {
        failure = error
      } finally {
        if (!cached) bitmap.recycle()
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

  /**
   * Re-applies the last wallpaper of each target using the current orientation,
   * without advancing the cursor. Used after a rotation so Android does not
   * keep showing the portrait crop in landscape (or vice versa).
   */
  suspend fun reapplyCurrent(target: ApplyTarget): ApplyResult =
    withContext(Dispatchers.IO) {
      mutex.withLock { reapplyLocked(target) }
    }

  /**
   * Fills the orientation cache for the current wallpaper without touching the
   * visible wallpaper. Called on app start so the first rotation is fast.
   */
  suspend fun warmOrientationCache(target: ApplyTarget) =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val state = repository.ensureLoaded()
        val orientation = currentOrientation()
        val (width, height) = displaySize()
        val targets = when (target) {
          ApplyTarget.BOTH -> listOf(ApplyTarget.HOME, ApplyTarget.LOCK)
          else -> listOf(target)
        }
        for (single in targets) {
          val config = state.config(single)
          if (config.lastAppliedAt <= 0L) continue
          val carousel = state.carousel(config.carouselId) ?: continue
          val candidates = carousel.wallpaperIds.mapNotNull { id ->
            val wallpaper = state.wallpaper(id) ?: return@mapNotNull null
            val variant = wallpaper.variant(orientation) ?: wallpaper.previewVariant()
              ?: return@mapNotNull null
            wallpaper to variant
          }
          if (candidates.isEmpty()) continue
          val index = config.lastWallpaperId
            ?.let { id -> candidates.indexOfFirst { it.first.id == id } }
            ?.takeIf { it >= 0 }
            ?: (((config.cursor - 1) % candidates.size + candidates.size) % candidates.size)
          val (wallpaper, _) = candidates[index]
          preRenderOtherOrientation(
            target = single,
            carouselId = config.carouselId,
            wallpaper = wallpaper,
            orientation = orientation,
            width = width,
            height = height,
            generation = currentGeneration(single),
          )
        }
      }
    }

  private suspend fun reapplyLocked(target: ApplyTarget): ApplyResult {
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
    var failure: Throwable? = null

    for (single in targets) {
      val config = state.config(single)
      if (config.lastAppliedAt <= 0L || config.lastOrientation == orientation) continue
      val carousel = state.carousel(config.carouselId) ?: continue
      sawCarousel = true

      val candidates = carousel.wallpaperIds.mapNotNull { id ->
        val wallpaper = state.wallpaper(id) ?: return@mapNotNull null
        val variant = wallpaper.variant(orientation) ?: wallpaper.previewVariant()
          ?: return@mapNotNull null
        wallpaper to variant
      }
      if (candidates.isEmpty()) continue

      val index = config.lastWallpaperId
        ?.let { id -> candidates.indexOfFirst { it.first.id == id } }
        ?.takeIf { it >= 0 }
        ?: (((config.cursor - 1) % candidates.size + candidates.size) % candidates.size)
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
      val cachedBitmap = cachedOrientation(single, cacheKey)
      val bitmap = cachedBitmap ?: WallpaperRenderer.render(
        file = imageStore.file(variant.fileName),
        variant = variant,
        targetWidth = screenWidth,
        targetHeight = screenHeight,
      ) ?: continue
      var keep = cachedBitmap != null

      try {
        val flags = if (single == ApplyTarget.LOCK) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        manager.setBitmap(bitmap, null, false, flags)
        applied += wallpaper.displayName()
        repository.update { current ->
          val updated = current.config(single).copy(
            lastWallpaperId = wallpaper.id,
            lastOrientation = orientation,
          )
          current.withConfig(single, updated)
        }
        if (cachedBitmap == null) {
          val generation = currentGeneration(single)
          storeOrientation(single, cacheKey, bitmap, generation)
          keep = true
          preRenderOtherOrientation(
            target = single,
            carouselId = config.carouselId,
            wallpaper = wallpaper,
            orientation = orientation,
            width = screenWidth,
            height = screenHeight,
            generation = generation,
          )
        } else {
          preRenderOtherOrientation(
            target = single,
            carouselId = config.carouselId,
            wallpaper = wallpaper,
            orientation = orientation,
            width = screenWidth,
            height = screenHeight,
            generation = currentGeneration(single),
          )
        }
      } catch (error: Exception) {
        failure = error
      } finally {
        if (!keep) bitmap.recycle()
      }
    }

    return when {
      applied.isNotEmpty() -> ApplyResult.Applied(target, applied)
      !sawCarousel -> ApplyResult.NoCarousel
      failure != null -> ApplyResult.Failed(failure)
      else -> ApplyResult.Skipped
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
