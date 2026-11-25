// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.wallcarousel.app.data.ImageStore
import com.wallcarousel.app.data.StateRepository
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.model.Carousel
import com.wallcarousel.app.data.model.Orientation
import com.wallcarousel.app.data.model.Playback
import com.wallcarousel.app.data.model.PlaybackItem
import com.wallcarousel.app.data.model.RotationChoice
import com.wallcarousel.app.data.model.Variant
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

  /** The carousel has wallpapers, but none is allowed in the current view. */
  data object NoVisibleWallpapers : ApplyResult
  data object NotSupported : ApplyResult

  /** Another trigger applied a wallpaper moments ago; this run did nothing. */
  data object Skipped : ApplyResult
  data class Failed(val cause: Throwable? = null) : ApplyResult
}

/**
 * Applies the next wallpaper of the carousel assigned to a target. The
 * orientation is read at apply time; static wallpapers do not change when the
 * device is rotated afterwards.
 *
 * Prepared wallpapers are cached as JPEG bytes instead of ARGB bitmaps, so the
 * four Home/Lock and portrait/landscape entries stay well below 5 MB and the
 * fast `setStream` path can reuse them directly.
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

  private class EncodedWallpaper(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
  )

  private data class CacheEntry(val key: CacheKey, val encoded: EncodedWallpaper)

  private val preRendered = mutableMapOf<ApplyTarget, CacheEntry>()
  private val cacheLock = Any()

  fun clearPreRendered() {
    synchronized(cacheLock) { preRendered.clear() }
  }

  private fun takePreRendered(target: ApplyTarget, key: CacheKey): EncodedWallpaper? =
    synchronized(cacheLock) {
      val entry = preRendered[target] ?: return null
      preRendered.remove(target)
      if (entry.key != key) return null
      entry.encoded
    }

  private fun hasPreRendered(target: ApplyTarget, key: CacheKey): Boolean =
    synchronized(cacheLock) { preRendered[target]?.key == key }

  private fun storePreRendered(target: ApplyTarget, key: CacheKey, encoded: EncodedWallpaper) {
    synchronized(cacheLock) {
      preRendered[target] = CacheEntry(key, encoded)
    }
  }

  // Both orientations of the currently applied wallpaper. Rotating the tablet
  // then only needs to write cached JPEG bytes instead of re-rendering.
  private val orientationCache = mutableMapOf<ApplyTarget, MutableMap<CacheKey, EncodedWallpaper>>()
  private val orientationGeneration = mutableMapOf<ApplyTarget, Long>()

  private fun currentGeneration(target: ApplyTarget): Long =
    synchronized(cacheLock) { orientationGeneration[target] ?: 0L }

  private fun newGeneration(target: ApplyTarget): Long =
    synchronized(cacheLock) {
      val next = (orientationGeneration[target] ?: 0L) + 1
      orientationGeneration[target] = next
      next
    }

  private fun cachedOrientation(target: ApplyTarget, key: CacheKey): EncodedWallpaper? =
    synchronized(cacheLock) { orientationCache[target]?.get(key) }

  private fun storeOrientation(
    target: ApplyTarget,
    key: CacheKey,
    encoded: EncodedWallpaper,
    generation: Long,
  ) {
    synchronized(cacheLock) {
      if ((orientationGeneration[target] ?: 0L) != generation) return
      val map = orientationCache.getOrPut(target) { mutableMapOf() }
      if (map.containsKey(key)) return
      map[key] = encoded
    }
  }

  private fun clearOrientation(target: ApplyTarget) {
    synchronized(cacheLock) { orientationCache.remove(target) }
  }

  private fun encode(bitmap: Bitmap): EncodedWallpaper? {
    return try {
      val output = ByteArrayOutputStream()
      val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
      val jpeg = output.toByteArray()
      if (!ok || jpeg.isEmpty()) {
        null
      } else {
        EncodedWallpaper(jpeg, bitmap.width, bitmap.height)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun decode(encoded: EncodedWallpaper): Bitmap? = try {
    BitmapFactory.decodeByteArray(
      encoded.jpeg,
      0,
      encoded.jpeg.size,
      BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
    )
  } catch (_: Exception) {
    null
  }

  private fun renderEncoded(
    variant: Variant,
    width: Int,
    height: Int,
  ): EncodedWallpaper? {
    val bitmap = WallpaperRenderer.render(
      file = imageStore.file(variant.fileName),
      variant = variant,
      targetWidth = width,
      targetHeight = height,
    ) ?: return null
    return try {
      encode(bitmap)
    } finally {
      bitmap.recycle()
    }
  }

  private fun writeStream(
    manager: WallpaperManager,
    encoded: EncodedWallpaper,
    flags: Int,
  ): Boolean = try {
    manager.setStream(ByteArrayInputStream(encoded.jpeg), null, false, flags)
    true
  } catch (_: Exception) {
    false
  }

  /**
   * Writes [variant] to the wallpaper manager and returns the encoded JPEG that
   * can be cached. [cached] is tried first, then a fresh render is encoded.
   */
  private fun writeWallpaper(
    manager: WallpaperManager,
    flags: Int,
    variant: Variant,
    width: Int,
    height: Int,
    cached: EncodedWallpaper?,
  ): EncodedWallpaper? {
    val useJpeg = repository.state.value?.jpegWallpaperWrite == true

    if (cached != null) {
      if (useJpeg && writeStream(manager, cached, flags)) {
        return cached
      }
      val bitmap = decode(cached)
      if (bitmap != null) {
        try {
          manager.setBitmap(bitmap, null, false, flags)
          return cached
        } catch (_: Exception) {
          // Fall through to a fresh render.
        } finally {
          bitmap.recycle()
        }
      }
    }

    val bitmap = WallpaperRenderer.render(
      file = imageStore.file(variant.fileName),
      variant = variant,
      targetWidth = width,
      targetHeight = height,
    ) ?: return null
    try {
      var encoded: EncodedWallpaper? = null
      if (useJpeg) {
        encoded = encode(bitmap)
        if (encoded != null && writeStream(manager, encoded, flags)) {
          return encoded
        }
      }
      manager.setBitmap(bitmap, null, false, flags)
      return encoded ?: encode(bitmap)
    } finally {
      bitmap.recycle()
    }
  }

  private fun preRenderOrientation(
    target: ApplyTarget,
    state: AppState,
    carousel: Carousel,
    orientation: Orientation,
    width: Int,
    height: Int,
    generation: Long,
  ) {
    val item = when (
      val choice = Playback.rotationChoice(state, state.config(target), carousel, orientation)
    ) {
      is RotationChoice.Keep -> choice.item
      is RotationChoice.Advance -> choice.item
      is RotationChoice.Fallback -> PlaybackItem(choice.wallpaper, choice.variant)
      RotationChoice.None -> return
    }
    val key = CacheKey(carousel.id, item.wallpaper.id, item.variant, orientation, width, height)
    if (cachedOrientation(target, key) != null) return
    appScope.launch(Dispatchers.Default) {
      val encoded = renderEncoded(item.variant, width, height) ?: return@launch
      storeOrientation(target, key, encoded, generation)
    }
  }

  private fun preRenderOtherOrientation(
    target: ApplyTarget,
    state: AppState,
    carousel: Carousel,
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
    preRenderOrientation(
      target = target,
      state = state,
      carousel = carousel,
      orientation = otherOrientation,
      width = height,
      height = width,
      generation = generation,
    )
  }

  private fun preRenderNext(
    target: ApplyTarget,
    carousel: Carousel,
    items: List<PlaybackItem>,
    nextIndex: Int,
    orientation: Orientation,
    width: Int,
    height: Int,
  ) {
    if (items.isEmpty() || repository.state.value?.preRenderNext != true) return
    val item = items[Playback.normalizedCursor(nextIndex, items.size)]
    val key = CacheKey(carousel.id, item.wallpaper.id, item.variant, orientation, width, height)
    if (hasPreRendered(target, key)) return
    appScope.launch(Dispatchers.Default) {
      val encoded = renderEncoded(item.variant, width, height) ?: return@launch
      if (repository.state.value?.preRenderNext != true) return@launch
      storePreRendered(target, key, encoded)
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
    var sawHiddenWallpapers = false
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

      val items = Playback.items(state, carousel, orientation)
      if (items.isEmpty()) {
        if (carousel.wallpaperIds.any { state.wallpaper(it) != null }) {
          sawHiddenWallpapers = true
        }
        continue
      }

      val index = Playback.normalizedCursor(config.cursor, items.size)
      val item = items[index]
      val (screenWidth, screenHeight) = displaySize()
      val cacheKey = CacheKey(
        carouselId = config.carouselId,
        wallpaperId = item.wallpaper.id,
        variant = item.variant,
        orientation = orientation,
        width = screenWidth,
        height = screenHeight,
      )
      val cached = if (state.preRenderNext) takePreRendered(single, cacheKey) else null

      try {
        val flags = if (single == ApplyTarget.LOCK) {
          WallpaperManager.FLAG_LOCK
        } else {
          WallpaperManager.FLAG_SYSTEM
        }
        val encoded = writeWallpaper(
          manager = manager,
          flags = flags,
          variant = item.variant,
          width = screenWidth,
          height = screenHeight,
          cached = cached,
        )
        if (encoded == null) {
          failure = IllegalStateException("Unable to render ${item.variant.fileName}")
          // Skip the unreadable entry so the carousel cannot get stuck on it.
          repository.update { current ->
            val updated = current.config(single).copy(cursor = index + 1)
            current.withConfig(single, updated)
          }
          continue
        }

        val updated = repository.update { current ->
          val configNow = current.config(single).copy(
            cursor = index + 1,
            lastAppliedAt = System.currentTimeMillis(),
            lastWallpaperId = item.wallpaper.id,
            lastOrientation = orientation,
          )
          current.withConfig(single, configNow)
        }
        val generation = newGeneration(single)
        clearOrientation(single)
        storeOrientation(single, cacheKey, encoded, generation)
        preRenderOtherOrientation(
          target = single,
          state = updated,
          carousel = carousel,
          orientation = orientation,
          width = screenWidth,
          height = screenHeight,
          generation = generation,
        )
        if (updated.preRenderNext) {
          preRenderNext(
            target = single,
            carousel = carousel,
            items = items,
            nextIndex = index + 1,
            orientation = orientation,
            width = screenWidth,
            height = screenHeight,
          )
        }
        applied += item.wallpaper.displayName()
      } catch (error: Exception) {
        failure = error
      }
    }

    return when {
      applied.isNotEmpty() -> ApplyResult.Applied(target, applied)
      !sawCarousel -> ApplyResult.NoCarousel
      failure != null -> ApplyResult.Failed(failure)
      skipped -> ApplyResult.Skipped
      sawHiddenWallpapers -> ApplyResult.NoVisibleWallpapers
      else -> ApplyResult.NoWallpapers
    }
  }

  /**
   * Re-applies each target using the current orientation, without advancing the
   * cursor when the current wallpaper is still allowed in the new view. If it is
   * hidden, the next visible wallpaper is used; if the view allows none, the
   * previous image is reused centred so nothing is left blank.
   */
  suspend fun reapplyCurrent(target: ApplyTarget): ApplyResult =
    withContext(Dispatchers.IO) {
      mutex.withLock { reapplyLocked(target) }
    }

  /**
   * Fills the orientation cache for the image that the next rotation will use,
   * without touching the visible wallpaper. Called on app start so the first
   * rotation is fast.
   */
  suspend fun warmOrientationCache(target: ApplyTarget) =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val state = repository.ensureLoaded()
        val orientation = currentOrientation()
        val (width, height) = displaySize()
        val otherOrientation = if (orientation == Orientation.PORTRAIT) {
          Orientation.LANDSCAPE
        } else {
          Orientation.PORTRAIT
        }
        val targets = when (target) {
          ApplyTarget.BOTH -> listOf(ApplyTarget.HOME, ApplyTarget.LOCK)
          else -> listOf(target)
        }
        for (single in targets) {
          val config = state.config(single)
          if (config.lastAppliedAt <= 0L) continue
          val carousel = state.carousel(config.carouselId) ?: continue
          preRenderOrientation(
            target = single,
            state = state,
            carousel = carousel,
            orientation = otherOrientation,
            width = height,
            height = width,
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

      val choice = Playback.rotationChoice(state, config, carousel, orientation)
      val item = when (choice) {
        is RotationChoice.Keep -> choice.item
        is RotationChoice.Advance -> choice.item
        is RotationChoice.Fallback -> PlaybackItem(choice.wallpaper, choice.variant)
        RotationChoice.None -> continue
      }
      val (screenWidth, screenHeight) = displaySize()
      val cacheKey = CacheKey(
        carouselId = config.carouselId,
        wallpaperId = item.wallpaper.id,
        variant = item.variant,
        orientation = orientation,
        width = screenWidth,
        height = screenHeight,
      )
      val cached = cachedOrientation(single, cacheKey)

      try {
        val flags = if (single == ApplyTarget.LOCK) {
          WallpaperManager.FLAG_LOCK
        } else {
          WallpaperManager.FLAG_SYSTEM
        }
        val encoded = writeWallpaper(
          manager = manager,
          flags = flags,
          variant = item.variant,
          width = screenWidth,
          height = screenHeight,
          cached = cached,
        ) ?: continue

        val now = System.currentTimeMillis()
        val updated = repository.update { current ->
          val configNow = current.config(single)
          val next = when (choice) {
            is RotationChoice.Advance -> configNow.copy(
              cursor = choice.index + 1,
              lastAppliedAt = now,
              lastWallpaperId = item.wallpaper.id,
              lastOrientation = orientation,
            )
            else -> configNow.copy(
              lastWallpaperId = item.wallpaper.id,
              lastOrientation = orientation,
            )
          }
          current.withConfig(single, next)
        }
        val generation = currentGeneration(single)
        storeOrientation(single, cacheKey, encoded, generation)
        preRenderOtherOrientation(
          target = single,
          state = updated,
          carousel = carousel,
          orientation = orientation,
          width = screenWidth,
          height = screenHeight,
          generation = generation,
        )
        applied += item.wallpaper.displayName()
      } catch (error: Exception) {
        failure = error
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
