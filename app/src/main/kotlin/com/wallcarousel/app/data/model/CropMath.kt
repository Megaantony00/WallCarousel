// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

/** Rectangle expressed in source-image pixels. */
data class CropRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
}

/**
 * Computes the source region that fills a target aspect ratio around a focal
 * point, honouring the zoom factor. The result never exceeds the source bounds.
 */
object CropMath {
  fun cropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    targetAspect: Float,
    focalX: Float,
    focalY: Float,
    zoom: Float,
  ): CropRect {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source size must be positive" }
    require(targetAspect > 0f) { "Target aspect must be positive" }

    val safeZoom = zoom.coerceAtLeast(1f)
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()

    val maxWidth: Float
    val maxHeight: Float
    if (sourceAspect > targetAspect) {
      maxHeight = sourceHeight.toFloat()
      maxWidth = maxHeight * targetAspect
    } else {
      maxWidth = sourceWidth.toFloat()
      maxHeight = maxWidth / targetAspect
    }

    val cropWidth = maxWidth / safeZoom
    val cropHeight = maxHeight / safeZoom

    val centerX = focalX.coerceIn(0f, 1f) * sourceWidth
    val centerY = focalY.coerceIn(0f, 1f) * sourceHeight

    val left = (centerX - cropWidth / 2f).coerceIn(0f, sourceWidth - cropWidth)
    val top = (centerY - cropHeight / 2f).coerceIn(0f, sourceHeight - cropHeight)

    return CropRect(left, top, left + cropWidth, top + cropHeight)
  }
}
