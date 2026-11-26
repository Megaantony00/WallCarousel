// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.wallcarousel.app.data.model.Orientation
import kotlin.math.max
import kotlin.math.min

/**
 * Aspect ratio of the physical display, independent of the current rotation.
 * The editor uses it so the preview matches what WallpaperManager will crop.
 */
object ScreenGeometry {
  fun aspectRatio(context: Context, orientation: Orientation): Float {
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    display.getRealMetrics(metrics)
    return aspectRatio(metrics.widthPixels, metrics.heightPixels, orientation)
  }

  fun aspectRatio(width: Int, height: Int, orientation: Orientation): Float {
    require(width > 0 && height > 0) { "Display size must be positive" }
    val shortSide = min(width, height).toFloat()
    val longSide = max(width, height).toFloat()
    return when (orientation) {
      Orientation.PORTRAIT -> shortSide / longSide
      Orientation.LANDSCAPE -> longSide / shortSide
    }
  }
}
