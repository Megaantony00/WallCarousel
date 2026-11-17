// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {
  @Test
  fun landscapeSourceIntoTallTargetKeepsFullHeight() {
    val rect = CropMath.cropRect(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 9f / 16f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 1f,
    )

    assertEquals(3000f * 9f / 16f, rect.width, 0.01f)
    assertEquals(3000f, rect.height, 0.01f)
    assertEquals(1156.25f, rect.left, 0.01f)
    assertEquals(0f, rect.top, 0.01f)
  }

  @Test
  fun zoomHalvesTheCropAroundTheFocalPoint() {
    val rect = CropMath.cropRect(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 9f / 16f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 2f,
    )

    assertEquals(3000f * 9f / 32f, rect.width, 0.01f)
    assertEquals(1500f, rect.height, 0.01f)
    assertEquals(2000f - rect.width / 2f, rect.left, 0.01f)
    assertEquals(750f, rect.top, 0.01f)
  }

  @Test
  fun focalPointNearTheEdgeIsClampedInsideTheSource() {
    val rect = CropMath.cropRect(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 16f / 9f,
      focalX = 0f,
      focalY = 1f,
      zoom = 1.5f,
    )

    assertTrue(rect.left >= 0f)
    assertTrue(rect.top >= 0f)
    assertTrue(rect.right <= 4000f)
    assertTrue(rect.bottom <= 3000f)
    assertEquals(16f / 9f, rect.width / rect.height, 0.001f)
  }

  @Test
  fun cropAlwaysMatchesTheRequestedAspect() {
    val rect = CropMath.cropRect(
      sourceWidth = 1080,
      sourceHeight = 2400,
      targetAspect = 16f / 10f,
      focalX = 0.3f,
      focalY = 0.8f,
      zoom = 1.2f,
    )

    assertEquals(16f / 10f, rect.width / rect.height, 0.001f)
  }
}
