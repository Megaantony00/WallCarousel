// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.wallcarousel.app.data.model.BandStyle
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
      if (variant.blurredBands) {
        renderBands(source, variant, width, height)
      } else {
        renderCover(source, variant, width, height)
      }
    } catch (error: OutOfMemoryError) {
      null
    } finally {
      if (!source.isRecycled) source.recycle()
    }
  }

  private fun renderCover(
    source: Bitmap,
    variant: Variant,
    width: Int,
    height: Int,
  ): Bitmap? {
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
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
      if (sourceRect.width() <= 0 || sourceRect.height() <= 0) {
        output.recycle()
        return null
      }
      val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        colorFilter = colorFilter(variant)
      }
      Canvas(output).drawBitmap(
        source,
        sourceRect,
        RectF(0f, 0f, width.toFloat(), height.toFloat()),
        paint,
      )
      if (variant.blur > 0) {
        val radius = (variant.blur * width / 1080f).roundToInt().coerceIn(1, 50)
        BitmapBlur.blur(output, radius)
      }
      return output
    } catch (error: Throwable) {
      output.recycle()
      throw error
    }
  }

  /**
   * Keeps the whole image visible and fills the missing sides with a blurred,
   * scaled copy of the same image. The style chooses whether that fill is
   * darkened or lightened.
   */
  private fun renderBands(
    source: Bitmap,
    variant: Variant,
    width: Int,
    height: Int,
  ): Bitmap? {
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return try {
      val canvas = Canvas(output)
      val filter = colorFilter(variant)
      val focalX = variant.focalX.coerceIn(0f, 1f)
      val focalY = variant.focalY.coerceIn(0f, 1f)

      // Blur a small copy instead of the full panel: much cheaper and smooth
      // once it is scaled back up.
      val backgroundWidth = max(1, width / 8)
      val backgroundHeight = max(
        1,
        (height.toFloat() * backgroundWidth / width).roundToInt(),
      )
      val background = Bitmap.createBitmap(
        backgroundWidth,
        backgroundHeight,
        Bitmap.Config.ARGB_8888,
      )
      try {
        val coverScale = max(
          backgroundWidth.toFloat() / source.width,
          backgroundHeight.toFloat() / source.height,
        )
        val backgroundWidthPx = source.width * coverScale
        val backgroundHeightPx = source.height * coverScale
        val backgroundLeft = (backgroundWidth - backgroundWidthPx) / 2f
        val backgroundTop = (backgroundHeight - backgroundHeightPx) / 2f
        Canvas(background).drawBitmap(
          source,
          null,
          RectF(
            backgroundLeft,
            backgroundTop,
            backgroundLeft + backgroundWidthPx,
            backgroundTop + backgroundHeightPx,
          ),
          Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = filter
          },
        )
        BitmapBlur.blur(background, max(2, backgroundWidth / 24))
        canvas.drawBitmap(
          background,
          null,
          RectF(0f, 0f, width.toFloat(), height.toFloat()),
          Paint(Paint.FILTER_BITMAP_FLAG),
        )
      } finally {
        background.recycle()
      }

      val overlay = Paint().apply {
        color = when (variant.bandStyle) {
          BandStyle.DARK -> Color.argb(118, 0, 0, 0)
          BandStyle.LIGHT -> Color.argb(92, 255, 255, 255)
        }
      }
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)

      val zoom = variant.zoom.coerceAtLeast(1f)
      val fit = min(
        width.toFloat() / source.width,
        height.toFloat() / source.height,
      ) * zoom
      val foregroundWidth = source.width * fit
      val foregroundHeight = source.height * fit
      val foregroundLeft = width / 2f - focalX * foregroundWidth
      val foregroundTop = height / 2f - focalY * foregroundHeight
      val destination = RectF(
        foregroundLeft,
        foregroundTop,
        foregroundLeft + foregroundWidth,
        foregroundTop + foregroundHeight,
      )
      val foregroundPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        colorFilter = filter
      }
      if (variant.blur > 0) {
        val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
          Canvas(layer).drawBitmap(source, null, destination, foregroundPaint)
          val radius = (variant.blur * width / 1080f).roundToInt().coerceIn(1, 50)
          BitmapBlur.blur(layer, radius)
          canvas.drawBitmap(layer, 0f, 0f, null)
        } finally {
          layer.recycle()
        }
      } else {
        canvas.drawBitmap(source, null, destination, foregroundPaint)
      }
      output
    } catch (error: Throwable) {
      output.recycle()
      throw error
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
