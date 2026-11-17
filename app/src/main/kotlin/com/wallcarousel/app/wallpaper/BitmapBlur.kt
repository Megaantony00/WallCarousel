// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.graphics.Bitmap

/**
 * Small, dependency-free Gaussian approximation built from repeated box blurs.
 * Runs on a worker thread; images are capped by [WallpaperRenderer.MAX_DIMENSION].
 */
object BitmapBlur {
  private const val PASSES = 2

  fun blur(bitmap: Bitmap, radius: Int) {
    if (radius < 1) return
    val width = bitmap.width
    val height = bitmap.height
    if (width < 3 || height < 3) return

    val safeRadius = radius.coerceAtMost(minOf(width, height) / 2)
    if (safeRadius < 1) return

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    repeat(PASSES) {
      blurHorizontal(pixels, width, height, safeRadius)
      blurVertical(pixels, width, height, safeRadius)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
  }

  private fun blurHorizontal(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val diameter = radius * 2 + 1
    val row = IntArray(width)

    for (y in 0 until height) {
      val base = y * width
      var sumA = 0
      var sumR = 0
      var sumG = 0
      var sumB = 0

      for (i in -radius..radius) {
        val color = pixels[base + i.coerceIn(0, width - 1)]
        sumA += color ushr 24 and 0xFF
        sumR += color shr 16 and 0xFF
        sumG += color shr 8 and 0xFF
        sumB += color and 0xFF
      }

      for (x in 0 until width) {
        row[x] = ((sumA / diameter) shl 24) or
          ((sumR / diameter) shl 16) or
          ((sumG / diameter) shl 8) or
          (sumB / diameter)

        val remove = pixels[base + (x - radius).coerceIn(0, width - 1)]
        val add = pixels[base + (x + radius + 1).coerceIn(0, width - 1)]
        sumA += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
        sumR += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
        sumG += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
        sumB += (add and 0xFF) - (remove and 0xFF)
      }

      System.arraycopy(row, 0, pixels, base, width)
    }
  }

  private fun blurVertical(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val diameter = radius * 2 + 1
    val column = IntArray(height)

    for (x in 0 until width) {
      var sumA = 0
      var sumR = 0
      var sumG = 0
      var sumB = 0

      for (i in -radius..radius) {
        val color = pixels[i.coerceIn(0, height - 1) * width + x]
        sumA += color ushr 24 and 0xFF
        sumR += color shr 16 and 0xFF
        sumG += color shr 8 and 0xFF
        sumB += color and 0xFF
      }

      for (y in 0 until height) {
        column[y] = ((sumA / diameter) shl 24) or
          ((sumR / diameter) shl 16) or
          ((sumG / diameter) shl 8) or
          (sumB / diameter)

        val remove = pixels[(y - radius).coerceIn(0, height - 1) * width + x]
        val add = pixels[(y + radius + 1).coerceIn(0, height - 1) * width + x]
        sumA += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
        sumR += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
        sumG += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
        sumB += (add and 0xFF) - (remove and 0xFF)
      }

      for (y in 0 until height) {
        pixels[y * width + x] = column[y]
      }
    }
  }
}
