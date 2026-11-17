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

/** Size of a crop rectangle before applying zoom. */
data class CropSize(val width: Float, val height: Float)

/** Focal point and zoom after a pan/zoom gesture. */
data class FocalZoom(val focalX: Float, val focalY: Float, val zoom: Float)

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
    val max = maxCropSize(sourceWidth, sourceHeight, targetAspect)

    val cropWidth = max.width / safeZoom
    val cropHeight = max.height / safeZoom

    val centerX = focalX.coerceIn(0f, 1f) * sourceWidth
    val centerY = focalY.coerceIn(0f, 1f) * sourceHeight

    val left = (centerX - cropWidth / 2f).coerceIn(0f, sourceWidth - cropWidth)
    val top = (centerY - cropHeight / 2f).coerceIn(0f, sourceHeight - cropHeight)

    return CropRect(left, top, left + cropWidth, top + cropHeight)
  }

  /** Largest crop with [targetAspect] that fits inside the source. */
  fun maxCropSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetAspect: Float,
  ): CropSize {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source size must be positive" }
    require(targetAspect > 0f) { "Target aspect must be positive" }

    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    return if (sourceAspect > targetAspect) {
      CropSize(width = sourceHeight * targetAspect, height = sourceHeight.toFloat())
    } else {
      CropSize(width = sourceWidth.toFloat(), height = sourceWidth / targetAspect)
    }
  }

  /**
   * Applies one pan/zoom gesture step.
   *
   * [centroidX] and [centroidY] are normalized to 0..1 inside the preview;
   * [panXFraction] and [panYFraction] are finger movement divided by the
   * preview size. The point under the fingers stays put while zooming, and
   * the image follows the fingers while panning. The resulting crop is always
   * clamped inside the source.
   */
  fun transform(
    sourceWidth: Int,
    sourceHeight: Int,
    targetAspect: Float,
    focalX: Float,
    focalY: Float,
    zoom: Float,
    centroidX: Float,
    centroidY: Float,
    panXFraction: Float,
    panYFraction: Float,
    zoomChange: Float,
    minZoom: Float = 1f,
    maxZoom: Float = 3f,
  ): FocalZoom {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source size must be positive" }
    require(targetAspect > 0f) { "Target aspect must be positive" }
    require(minZoom > 0f && maxZoom >= minZoom) { "Invalid zoom range" }

    val safeZoom = zoom.coerceIn(minZoom, maxZoom)
    val current = cropRect(sourceWidth, sourceHeight, targetAspect, focalX, focalY, safeZoom)
    val cx = centroidX.coerceIn(0f, 1f)
    val cy = centroidY.coerceIn(0f, 1f)
    val anchorX = current.left + cx * current.width
    val anchorY = current.top + cy * current.height

    val newZoom = (safeZoom * zoomChange.coerceAtLeast(0.01f)).coerceIn(minZoom, maxZoom)
    val max = maxCropSize(sourceWidth, sourceHeight, targetAspect)
    val cropWidth = (max.width / newZoom).coerceAtMost(sourceWidth.toFloat())
    val cropHeight = (max.height / newZoom).coerceAtMost(sourceHeight.toFloat())

    var left = anchorX - cx * cropWidth - panXFraction * cropWidth
    var top = anchorY - cy * cropHeight - panYFraction * cropHeight
    left = left.coerceIn(0f, (sourceWidth - cropWidth).coerceAtLeast(0f))
    top = top.coerceIn(0f, (sourceHeight - cropHeight).coerceAtLeast(0f))

    return FocalZoom(
      focalX = ((left + cropWidth / 2f) / sourceWidth).coerceIn(0f, 1f),
      focalY = ((top + cropHeight / 2f) / sourceHeight).coerceIn(0f, 1f),
      zoom = newZoom,
    )
  }
}
