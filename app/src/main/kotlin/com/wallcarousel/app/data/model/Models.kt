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
) {
  /** Custom name when set, otherwise the gallery/import name. */
  fun displayName(): String = name.ifBlank { sourceName }

  fun variant(orientation: Orientation): Variant? = when (orientation) {
    Orientation.PORTRAIT -> portrait
    Orientation.LANDSCAPE -> landscape
  }

  fun hasVariant(orientation: Orientation): Boolean = variant(orientation) != null

  fun availableOrientations(): List<Orientation> =
    Orientation.entries.filter(::hasVariant)

  /** Variant used to draw a thumbnail, regardless of the current orientation. */
  fun previewVariant(): Variant? = portrait ?: landscape
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
  val preRenderNext: Boolean = false,
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

  /** Number of wallpapers of [carousel] that still exist in the library. */
  fun wallpaperCount(carousel: Carousel): Int =
    carousel.wallpaperIds.count { id -> wallpapers.any { it.id == id } }

  /** True when Home or Lock advances on unlock and has a carousel assigned. */
  fun hasWakeTarget(): Boolean = listOf(home, lock).any { config ->
    config.schedule.wake && carousel(config.carouselId) != null
  }
}
