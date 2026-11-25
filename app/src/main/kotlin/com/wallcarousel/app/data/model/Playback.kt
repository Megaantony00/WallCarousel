// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

/** A wallpaper together with the variant that will be rendered for a view. */
data class PlaybackItem(val wallpaper: Wallpaper, val variant: Variant)

/** What a rotation should show, decided before touching the wallpaper manager. */
sealed interface RotationChoice {
  /** The wallpaper that was already applied can also be shown in the new view. */
  data class Keep(val item: PlaybackItem, val index: Int) : RotationChoice

  /** The current wallpaper is hidden in the new view; use the next visible one. */
  data class Advance(val item: PlaybackItem, val index: Int) : RotationChoice

  /** No wallpaper is visible in the new view; keep the previous image centred. */
  data class Fallback(val wallpaper: Wallpaper, val variant: Variant) : RotationChoice

  data object None : RotationChoice
}

/**
 * Pure selection rules shared by normal applies, rotation and preloading.
 *
 * Visibility is a property of the wallpaper, so the same rules apply to every
 * carousel. A rotation keeps the current wallpaper when the new view allows it
 * and otherwise advances to the next visible wallpaper. When the view allows
 * none of them the previous image is reused, centred and without bands, so the
 * screen never goes blank and no runtime warning is needed.
 */
object Playback {
  fun items(
    state: AppState,
    carousel: Carousel,
    orientation: Orientation,
  ): List<PlaybackItem> = carousel.wallpaperIds.mapNotNull { id ->
    val wallpaper = state.wallpaper(id) ?: return@mapNotNull null
    if (!wallpaper.isVisibleIn(orientation)) return@mapNotNull null
    val variant = wallpaper.variant(orientation) ?: wallpaper.previewVariant()
      ?: return@mapNotNull null
    PlaybackItem(wallpaper, variant)
  }

  fun normalizedCursor(cursor: Int, size: Int): Int =
    if (size <= 0) 0 else ((cursor % size) + size) % size

  fun rotationChoice(
    state: AppState,
    config: TargetConfig,
    carousel: Carousel,
    orientation: Orientation,
  ): RotationChoice {
    val items = items(state, carousel, orientation)
    val currentId = config.lastWallpaperId
    val previousFromCursor = state.wallpaper(
      carousel.wallpaperIds.getOrNull(
        normalizedCursor(config.cursor - 1, carousel.wallpaperIds.size),
      ),
    )

    if (currentId != null) {
      val index = items.indexOfFirst { it.wallpaper.id == currentId }
      if (index >= 0) return RotationChoice.Keep(items[index], index)
    } else {
      val derived = previousFromCursor?.id
      if (derived != null) {
        val index = items.indexOfFirst { it.wallpaper.id == derived }
        if (index >= 0) return RotationChoice.Keep(items[index], index)
      }
    }

    if (items.isNotEmpty() && carousel.wallpaperIds.isNotEmpty()) {
      // Walk the carousel in its real order. The cursor indexes the filtered
      // list of one view, so it cannot be used for the other view directly.
      val currentPosition = currentId?.let { carousel.wallpaperIds.indexOf(it) } ?: -1
      val start = if (currentPosition >= 0) {
        currentPosition + 1
      } else {
        normalizedCursor(config.cursor, carousel.wallpaperIds.size)
      }
      for (offset in carousel.wallpaperIds.indices) {
        val id = carousel.wallpaperIds[(start + offset) % carousel.wallpaperIds.size]
        val index = items.indexOfFirst { it.wallpaper.id == id }
        if (index >= 0) return RotationChoice.Advance(items[index], index)
      }
    }

    val previous = state.wallpaper(currentId) ?: previousFromCursor ?: return RotationChoice.None
    val previousVariant = previous.variant(orientation) ?: previous.previewVariant()
      ?: return RotationChoice.None
    return RotationChoice.Fallback(previous, fallbackVariant(previousVariant))
  }

  /** Centred cover used when the new view has no visible wallpaper. */
  fun fallbackVariant(variant: Variant): Variant = variant.copy(
    focalX = 0.5f,
    focalY = 0.5f,
    zoom = 1f,
    blurredBands = false,
  )
}
