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

  @Test
  fun maxCropSizeFillsTheSourceWithoutOverflow() {
    val landscape = CropMath.maxCropSize(4000, 3000, 9f / 16f)
    assertEquals(1687.5f, landscape.width, 0.01f)
    assertEquals(3000f, landscape.height, 0.01f)

    val portrait = CropMath.maxCropSize(1080, 2400, 16f / 10f)
    assertEquals(1080f, portrait.width, 0.01f)
    assertEquals(675f, portrait.height, 0.01f)
  }

  @Test
  fun panMovesTheCropOppositeToTheFinger() {
    val result = CropMath.transform(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 9f / 16f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 2f,
      centroidX = 0.5f,
      centroidY = 0.5f,
      panXFraction = 0.1f,
      panYFraction = 0f,
      zoomChange = 1f,
    )

    assertTrue(result.focalX < 0.5f)
    assertEquals(2f, result.zoom, 0.0001f)
    val rect = CropMath.cropRect(
      4000, 3000, 9f / 16f, result.focalX, result.focalY, result.zoom,
    )
    assertEquals(843.75f, rect.width, 0.01f)
  }

  @Test
  fun zoomKeepsTheAnchorUnderTheCentroid() {
    val before = CropMath.cropRect(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 16f / 10f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 1f,
    )
    val centroidX = 0.25f
    val centroidY = 0.3f
    val anchorBeforeX = before.left + centroidX * before.width
    val anchorBeforeY = before.top + centroidY * before.height

    val result = CropMath.transform(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 16f / 10f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 1f,
      centroidX = centroidX,
      centroidY = centroidY,
      panXFraction = 0f,
      panYFraction = 0f,
      zoomChange = 2f,
    )
    val after = CropMath.cropRect(
      4000, 3000, 16f / 10f, result.focalX, result.focalY, result.zoom,
    )

    assertEquals(2f, result.zoom, 0.0001f)
    assertEquals(anchorBeforeX, after.left + centroidX * after.width, 0.5f)
    assertEquals(anchorBeforeY, after.top + centroidY * after.height, 0.5f)
  }

  @Test
  fun transformClampsZoomAndCropInsideTheSource() {
    val zoomedIn = CropMath.transform(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 9f / 16f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 2.8f,
      centroidX = 0.5f,
      centroidY = 0.5f,
      panXFraction = 0f,
      panYFraction = 0f,
      zoomChange = 10f,
    )
    val zoomedOut = CropMath.transform(
      sourceWidth = 4000,
      sourceHeight = 3000,
      targetAspect = 9f / 16f,
      focalX = 0.5f,
      focalY = 0.5f,
      zoom = 1f,
      centroidX = 0.5f,
      centroidY = 0.5f,
      panXFraction = 0f,
      panYFraction = 0f,
      zoomChange = 0.1f,
    )

    assertEquals(3f, zoomedIn.zoom, 0.0001f)
    assertEquals(1f, zoomedOut.zoom, 0.0001f)

    val rect = CropMath.cropRect(
      4000, 3000, 9f / 16f, zoomedIn.focalX, zoomedIn.focalY, zoomedIn.zoom,
    )
    assertTrue(rect.left >= 0f)
    assertTrue(rect.top >= 0f)
    assertTrue(rect.right <= 4000f)
    assertTrue(rect.bottom <= 3000f)
  }
}
