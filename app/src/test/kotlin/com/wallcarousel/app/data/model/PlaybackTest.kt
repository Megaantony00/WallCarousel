// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTest {
  private fun wallpaper(
    id: String,
    visiblePortrait: Boolean = true,
    visibleLandscape: Boolean = true,
  ) = Wallpaper(
    id = id,
    name = id,
    portrait = Variant(fileName = "$id-p.jpg", width = 1200, height = 1600),
    landscape = Variant(fileName = "$id-l.jpg", width = 1600, height = 1000),
    visiblePortrait = visiblePortrait,
    visibleLandscape = visibleLandscape,
  )

  private fun state(
    vararg wallpapers: Wallpaper,
    carouselIds: List<String> = wallpapers.map { it.id },
  ) = AppState(
    wallpapers = wallpapers.toList(),
    carousels = listOf(Carousel(id = "c1", name = "Test", wallpaperIds = carouselIds)),
  )

  @Test
  fun itemsRespectPerWallpaperVisibility() {
    val portraitOnly = wallpaper("a", visibleLandscape = false)
    val both = wallpaper("b")
    val current = state(portraitOnly, both)

    assertEquals(
      listOf("a", "b"),
      Playback.items(current, current.carousels.first(), Orientation.PORTRAIT)
        .map { it.wallpaper.id },
    )
    assertEquals(
      listOf("b"),
      Playback.items(current, current.carousels.first(), Orientation.LANDSCAPE)
        .map { it.wallpaper.id },
    )
  }

  @Test
  fun rotationKeepsTheCurrentWallpaperWhenItIsVisible() {
    val a = wallpaper("a")
    val b = wallpaper("b")
    val current = state(a, b)
    val config = TargetConfig(carouselId = "c1", lastWallpaperId = "a", cursor = 1)

    val choice = Playback.rotationChoice(
      current,
      config,
      current.carousels.first(),
      Orientation.LANDSCAPE,
    )

    assertTrue(choice is RotationChoice.Keep)
    assertEquals("a", (choice as RotationChoice.Keep).item.wallpaper.id)
  }

  @Test
  fun rotationAdvancesToTheNextVisibleWallpaper() {
    val a = wallpaper("a", visibleLandscape = false)
    val b = wallpaper("b", visibleLandscape = false)
    val c = wallpaper("c")
    val current = state(a, b, c, carouselIds = listOf("a", "b", "c"))
    val config = TargetConfig(carouselId = "c1", lastWallpaperId = "b", cursor = 2)

    val choice = Playback.rotationChoice(
      current,
      config,
      current.carousels.first(),
      Orientation.LANDSCAPE,
    )

    assertTrue(choice is RotationChoice.Advance)
    choice as RotationChoice.Advance
    assertEquals("c", choice.item.wallpaper.id)
    assertEquals(0, choice.index)
  }

  @Test
  fun rotationWalksTheRealCarouselOrderAcrossViews() {
    val a = wallpaper("a", visibleLandscape = false)
    val b = wallpaper("b", visiblePortrait = false)
    val c = wallpaper("c")
    val current = state(a, b, c, carouselIds = listOf("a", "b", "c"))
    val config = TargetConfig(carouselId = "c1", lastWallpaperId = "a", cursor = 1)

    val choice = Playback.rotationChoice(
      current,
      config,
      current.carousels.first(),
      Orientation.LANDSCAPE,
    )

    assertTrue(choice is RotationChoice.Advance)
    choice as RotationChoice.Advance
    assertEquals("b", choice.item.wallpaper.id)
    assertEquals(0, choice.index)
  }

  @Test
  fun rotationFallsBackToThePreviousImageWhenTheViewAllowsNone() {
    val a = wallpaper("a", visibleLandscape = false)
    val current = state(a)
    val config = TargetConfig(carouselId = "c1", lastWallpaperId = "a", cursor = 1)
    val fallbackVariant = Playback.fallbackVariant(a.landscape!!)

    val choice = Playback.rotationChoice(
      current,
      config,
      current.carousels.first(),
      Orientation.LANDSCAPE,
    )

    assertTrue(choice is RotationChoice.Fallback)
    choice as RotationChoice.Fallback
    assertEquals("a", choice.wallpaper.id)
    assertEquals(fallbackVariant, choice.variant)
    assertEquals(0.5f, choice.variant.focalX)
    assertEquals(0.5f, choice.variant.focalY)
    assertEquals(1f, choice.variant.zoom)
    assertFalse(choice.variant.blurredBands)
  }

  @Test
  fun emptyCarouselHasNoRotationChoice() {
    val current = AppState(
      carousels = listOf(Carousel(id = "c1", name = "Empty")),
    )
    val config = TargetConfig(carouselId = "c1", lastWallpaperId = null, cursor = 0)

    assertEquals(
      RotationChoice.None,
      Playback.rotationChoice(
        current,
        config,
        current.carousels.first(),
        Orientation.PORTRAIT,
      ),
    )
  }

  @Test
  fun hiddenWallpaperIsNotReportedAsVisible() {
    val a = wallpaper("a", visibleLandscape = false)
    val current = state(a)

    assertTrue(current.hasVisibleWallpaper(current.carousels.first(), Orientation.PORTRAIT))
    assertFalse(current.hasVisibleWallpaper(current.carousels.first(), Orientation.LANDSCAPE))
  }

  @Test
  fun visibilityAlwaysKeepsAtLeastOneViewActive() {
    val hidden = Wallpaper(
      id = "a",
      name = "a",
      visiblePortrait = false,
      visibleLandscape = false,
    )

    assertTrue(hidden.withGuaranteedVisibility().visiblePortrait)
    assertFalse(hidden.withGuaranteedVisibility().visibleLandscape)
  }

  @Test
  fun thumbnailPrefersVisibleVariantWithoutBands() {
    val wallpaper = wallpaper("a").copy(
      portrait = Variant("a-p.jpg", 1200, 1600, blurredBands = true),
      landscape = Variant("a-l.jpg", 1600, 1000, blurredBands = false),
    )

    assertEquals("a-l.jpg", wallpaper.thumbnailVariant()?.fileName)
  }

  @Test
  fun thumbnailDoesNotUseAHiddenVariant() {
    val wallpaper = wallpaper("a", visibleLandscape = false).copy(
      portrait = Variant("a-p.jpg", 1200, 1600, blurredBands = true),
      landscape = Variant("a-l.jpg", 1600, 1000, blurredBands = false),
    )

    assertEquals("a-p.jpg", wallpaper.thumbnailVariant()?.fileName)
  }
}
