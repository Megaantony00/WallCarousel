// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.wallcarousel.app.data.model.CropMath
import com.wallcarousel.app.data.model.Variant
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a [Variant] into a bitmap of the requested size. The crop is applied
 * non-destructively from the original file, so the same recipe works for both
 * previews and the final wallpaper.
 */
object WallpaperRenderer {
  /** Covers the 2880x1800 panel of the target tablet without upscaling. */
  const val MAX_DIMENSION = 3200

  fun render(
    file: File,
    variant: Variant,
    targetWidth: Int,
    targetHeight: Int,
  ): Bitmap? {
    if (!file.exists() || targetWidth <= 0 || targetHeight <= 0) return null

    val width = targetWidth.coerceAtMost(MAX_DIMENSION)
    val height = (width.toFloat() * targetHeight / targetWidth)
      .roundToInt()
      .coerceIn(1, MAX_DIMENSION)

    val source = runCatching {
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val zoom = variant.zoom.coerceAtLeast(1f)
        val neededWidth = (width / zoom).roundToInt().coerceAtLeast(1)
        val neededHeight = (height / zoom).roundToInt().coerceAtLeast(1)
        val sample = min(
          info.size.width / neededWidth,
          info.size.height / neededHeight,
        )
        decoder.setTargetSampleSize(max(1, sample))
      }
    }.getOrNull() ?: return null

    return try {
      val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val crop = CropMath.cropRect(
        sourceWidth = source.width,
        sourceHeight = source.height,
        targetAspect = width.toFloat() / height.toFloat(),
        focalX = variant.focalX,
        focalY = variant.focalY,
        zoom = variant.zoom,
      )
      val sourceRect = Rect(
        crop.left.roundToInt().coerceAtLeast(0),
        crop.top.roundToInt().coerceAtLeast(0),
        crop.right.roundToInt().coerceAtMost(source.width),
        crop.bottom.roundToInt().coerceAtMost(source.height),
      )
      if (sourceRect.width() <= 0 || sourceRect.height() <= 0) return null
      val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        colorFilter = colorFilter(variant)
      }
      Canvas(output).drawBitmap(
        source,
        sourceRect,
        RectF(0f, 0f, width.toFloat(), height.toFloat()),
        paint,
      )
      source.recycle()

      if (variant.blur > 0) {
        val radius = (variant.blur * width / 1080f).roundToInt().coerceIn(1, 50)
        BitmapBlur.blur(output, radius)
      }
      output
    } catch (error: OutOfMemoryError) {
      null
    } finally {
      if (!source.isRecycled) source.recycle()
    }
  }

  internal fun colorFilter(variant: Variant): ColorFilter? {
    val saturation = variant.saturation.coerceIn(0f, 2f)
    val contrast = variant.contrast.coerceIn(0.5f, 1.5f)
    val brightness = variant.brightness.coerceIn(-0.5f, 0.5f)
    if (saturation == 1f && contrast == 1f && brightness == 0f) return null

    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    val offset = (1f - contrast) * 127.5f + brightness * 255f
    matrix.postConcat(
      ColorMatrix(
        floatArrayOf(
          contrast, 0f, 0f, 0f, offset,
          0f, contrast, 0f, 0f, offset,
          0f, 0f, contrast, 0f, offset,
          0f, 0f, 0f, 1f, 0f,
        ),
      ),
    )
    return ColorMatrixColorFilter(matrix)
  }
}
