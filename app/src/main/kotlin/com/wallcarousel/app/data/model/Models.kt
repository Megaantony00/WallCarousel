// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

import kotlinx.serialization.Serializable

/** Orientation of the asset that is displayed on screen. */
@Serializable
enum class Orientation {
  PORTRAIT,
  LANDSCAPE,
}

/** Where a carousel is applied. */
@Serializable
enum class ApplyTarget {
  HOME,
  LOCK,
  BOTH,
}

@Serializable
enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

/** Colour treatment of the blurred fill used by the [Variant.blurredBands] mode. */
@Serializable
enum class BandStyle {
  DARK,
  LIGHT,
}

/**
 * Independent triggers for a target. Any combination is valid; when all of
 * them are off the target advances only when the user asks.
 *
 * [intervalMinutes] is null when the interval trigger is off, [dailyMinutes]
 * contains local times expressed as minutes from midnight and [wake] enables
 * the advance on every unlock.
 */
@Serializable
data class Schedule(
  val intervalMinutes: Int? = null,
  val dailyMinutes: List<Int> = emptyList(),
  val wake: Boolean = false,
) {
  val isManual: Boolean
    get() = intervalMinutes == null && dailyMinutes.isEmpty() && !wake
}

/** Assignment of a carousel to the home or lock screen. */
@Serializable
data class TargetConfig(
  val carouselId: String? = null,
  val schedule: Schedule = Schedule(),
  val cursor: Int = 0,
  /** Last successful apply, used to avoid two triggers advancing together. */
  val lastAppliedAt: Long = 0L,
  /** Wallpaper most recently applied, used to seed the app palette. */
  val lastWallpaperId: String? = null,
  /** Orientation used for the last apply, so rotation can re-crop it. */
  val lastOrientation: Orientation? = null,
)

/**
 * One imported image together with its non-destructive editing recipe.
 *
 * The crop is described by a focal point and a zoom factor so that the same
 * recipe can be rendered at any output resolution.
 */
@Serializable
data class Variant(
  val fileName: String,
  val width: Int,
  val height: Int,
  val focalX: Float = 0.5f,
  val focalY: Float = 0.5f,
  val zoom: Float = 1f,
  val brightness: Float = 0f,
  val contrast: Float = 1f,
  val saturation: Float = 1f,
  val blur: Int = 0,
  /** Keeps the whole image and fills the missing sides with a blurred copy. */
  val blurredBands: Boolean = false,
  /** Colour treatment of the blurred fill, used only when [blurredBands] is on. */
  val bandStyle: BandStyle = BandStyle.DARK,
) : java.io.Serializable

/** A wallpaper with optional portrait and landscape versions. */
@Serializable
data class Wallpaper(
  val id: String,
  val name: String,
  val portrait: Variant? = null,
  val landscape: Variant? = null,
  val importedAt: Long = 0L,
  /** Name suggested by the gallery or the import time; used as a placeholder. */
  val sourceName: String = "",
  /** Whether carousels may show this wallpaper with the device in portrait. */
  val visiblePortrait: Boolean = true,
  /** Whether carousels may show this wallpaper with the device in landscape. */
  val visibleLandscape: Boolean = true,
) {
  /** Custom name when set, otherwise the gallery/import name. */
  fun displayName(): String = name.ifBlank { sourceName }

  fun variant(orientation: Orientation): Variant? = when (orientation) {
    Orientation.PORTRAIT -> portrait
    Orientation.LANDSCAPE -> landscape
  }

  fun hasVariant(orientation: Orientation): Boolean = variant(orientation) != null

  /** Visibility of this wallpaper in [orientation], independent of its variants. */
  fun isVisibleIn(orientation: Orientation): Boolean = when (orientation) {
    Orientation.PORTRAIT -> visiblePortrait
    Orientation.LANDSCAPE -> visibleLandscape
  }

  fun hasAnyVisibleView(): Boolean = visiblePortrait || visibleLandscape

  /**
   * Restores the invariant that at least one view stays active. Used when
   * reading old or hand-edited backups.
   */
  fun withGuaranteedVisibility(): Wallpaper =
    if (hasAnyVisibleView()) this else copy(visiblePortrait = true)

  fun availableOrientations(): List<Orientation> =
    Orientation.entries.filter(::hasVariant)

  /** Variant used to draw a thumbnail, regardless of the current orientation. */
  fun previewVariant(): Variant? = portrait ?: landscape

  /**
   * Variant used by library cards: prefer a visible version without blurred
   * bands, because usually one orientation uses bands and the other does not.
   */
  fun thumbnailVariant(): Variant? {
    val visible = listOfNotNull(
      if (visiblePortrait) portrait else null,
      if (visibleLandscape) landscape else null,
    )
    val candidates = visible.ifEmpty { listOfNotNull(portrait, landscape) }
    return candidates.firstOrNull { !it.blurredBands } ?: candidates.firstOrNull()
  }
}

/** An ordered list of wallpapers. */
@Serializable
data class Carousel(
  val id: String,
  val name: String,
  val wallpaperIds: List<String> = emptyList(),
  val createdAt: Long = 0L,
)

/** Everything WallCarousel persists locally. */
@Serializable
data class AppState(
  val schemaVersion: Int = 2,
  val wallpapers: List<Wallpaper> = emptyList(),
  val carousels: List<Carousel> = emptyList(),
  val home: TargetConfig = TargetConfig(),
  val lock: TargetConfig = TargetConfig(),
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val dynamicColor: Boolean = true,
  val wallpaperPalette: Boolean = false,
  val wallpaperPaletteTarget: ApplyTarget = ApplyTarget.HOME,
  val preRenderNext: Boolean = false,
  val jpegWallpaperWrite: Boolean = true,
  val autoUpdateCheck: Boolean = false,
  val lastUpdateCheckAt: Long = 0L,
) {
  fun wallpaper(id: String?): Wallpaper? = wallpapers.firstOrNull { it.id == id }

  fun carousel(id: String?): Carousel? = carousels.firstOrNull { it.id == id }

  fun config(target: ApplyTarget): TargetConfig = when (target) {
    ApplyTarget.LOCK -> lock
    else -> home
  }

  fun withConfig(target: ApplyTarget, config: TargetConfig): AppState = when (target) {
    ApplyTarget.LOCK -> copy(lock = config)
    else -> copy(home = config)
  }

  /** Variant of the wallpaper most recently applied to [target]. */
  fun currentVariant(target: ApplyTarget): Variant? {
    val targetConfig = config(target)
    targetConfig.lastWallpaperId?.let { id ->
      wallpaper(id)?.previewVariant()?.let { return it }
    }
    val carousel = carousel(targetConfig.carouselId) ?: return null
    if (targetConfig.lastAppliedAt <= 0L) return null
    val candidates = carousel.wallpaperIds.mapNotNull { id ->
      wallpaper(id)?.previewVariant()
    }
    if (candidates.isEmpty()) return null
    val index = ((targetConfig.cursor - 1) % candidates.size + candidates.size) % candidates.size
    return candidates[index]
  }

  /** Number of wallpapers of [carousel] that still exist in the library. */
  fun wallpaperCount(carousel: Carousel): Int =
    carousel.wallpaperIds.count { id -> wallpapers.any { it.id == id } }

  /** Wallpapers of [carousel] that can be shown in [orientation]. */
  fun visibleWallpapers(carousel: Carousel, orientation: Orientation): List<Wallpaper> =
    carousel.wallpaperIds.mapNotNull(::wallpaper)
      .filter { it.isVisibleIn(orientation) && it.previewVariant() != null }

  /** True when [carousel] has at least one wallpaper for [orientation]. */
  fun hasVisibleWallpaper(carousel: Carousel, orientation: Orientation): Boolean =
    carousel.wallpaperIds.any { id ->
      val wallpaper = wallpaper(id)
      wallpaper != null && wallpaper.isVisibleIn(orientation) &&
        wallpaper.previewVariant() != null
    }

  /** True when Home or Lock advances on unlock and has a carousel assigned. */
  fun hasWakeTarget(): Boolean = listOf(home, lock).any { config ->
    config.schedule.wake && carousel(config.carouselId) != null
  }
}
