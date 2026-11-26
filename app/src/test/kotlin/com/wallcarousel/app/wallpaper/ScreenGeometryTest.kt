// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import com.wallcarousel.app.data.model.Orientation
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGeometryTest {
  @Test
  fun portraitUsesShortSideOverLongSide() {
    assertEquals(
      0.625f,
      ScreenGeometry.aspectRatio(2880, 1800, Orientation.PORTRAIT),
      0.0001f,
    )
  }

  @Test
  fun landscapeUsesLongSideOverShortSide() {
    assertEquals(
      1.6f,
      ScreenGeometry.aspectRatio(2880, 1800, Orientation.LANDSCAPE),
      0.0001f,
    )
  }

  @Test
  fun aspectDoesNotDependOnCurrentRotation() {
    assertEquals(
      ScreenGeometry.aspectRatio(2880, 1800, Orientation.PORTRAIT),
      ScreenGeometry.aspectRatio(1800, 2880, Orientation.PORTRAIT),
      0.0001f,
    )
    assertEquals(
      ScreenGeometry.aspectRatio(2880, 1800, Orientation.LANDSCAPE),
      ScreenGeometry.aspectRatio(1800, 2880, Orientation.LANDSCAPE),
      0.0001f,
    )
  }
}
