// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.theme

import android.app.WallpaperColors
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.wallcarousel.app.data.model.Variant
import java.io.File

/** The image currently applied to Home, used to seed the app palette. */
data class WallpaperPaletteSource(val file: File, val variant: Variant)

/**
 * Builds a Material color scheme from the colors of the current Home wallpaper.
 * This is a small, readable palette: accents follow the wallpaper while the
 * surfaces stay the same as the static theme.
 */
@Composable
fun rememberWallpaperColorScheme(
  darkTheme: Boolean,
  source: WallpaperPaletteSource?,
): ColorScheme = remember(source, darkTheme) {
  buildWallpaperScheme(readWallpaperSeeds(source), darkTheme)
    ?: if (darkTheme) DarkColorScheme else LightColorScheme
}

private data class WallpaperSeeds(
  val primary: Int,
  val secondary: Int,
  val tertiary: Int,
)

private fun readWallpaperSeeds(source: WallpaperPaletteSource?): WallpaperSeeds? {
  source ?: return null
  return runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 96 && bounds.outHeight / (sample * 2) >= 96) {
      sample *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
      source.file.path,
      BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null

    try {
      // The system extractor gives us the same kind of seed colors used by
      // Material You, but from our own image and without extra permissions.
      val colors = WallpaperColors.fromBitmap(bitmap) ?: return null
      val primary = colorToArgb(colors, "getPrimaryColor") ?: return null
      WallpaperSeeds(
        primary = primary,
        secondary = colorToArgb(colors, "getSecondaryColor") ?: primary,
        tertiary = colorToArgb(colors, "getTertiaryColor") ?: primary,
      )
    } finally {
      bitmap.recycle()
    }
  }.getOrNull()
}

/**
 * Android 16 changed WallpaperColors to return android.graphics.Color objects;
 * older versions return ARGB ints. Reflection keeps one implementation for both.
 */
private fun colorToArgb(colors: Any, methodName: String): Int? = runCatching {
  val value = colors.javaClass.getMethod(methodName).invoke(colors) ?: return null
  when (value) {
    is Int -> value
    else -> value.javaClass.getMethod("toArgb").invoke(value) as? Int
  }
}.getOrNull()

private fun buildWallpaperScheme(seeds: WallpaperSeeds?, darkTheme: Boolean): ColorScheme? {
  seeds ?: return null
  val base = if (darkTheme) DarkColorScheme else LightColorScheme
  val primary = accent(seeds.primary, darkTheme)
  val secondary = accent(seeds.secondary, darkTheme)
  val tertiary = accent(seeds.tertiary, darkTheme)
  val primaryContainer = accentContainer(seeds.primary, darkTheme)
  val secondaryContainer = accentContainer(seeds.secondary, darkTheme)
  val tertiaryContainer = accentContainer(seeds.tertiary, darkTheme)

  return base.copy(
    primary = primary,
    onPrimary = readableOn(primary),
    primaryContainer = primaryContainer,
    onPrimaryContainer = readableOn(primaryContainer),
    inversePrimary = accent(seeds.primary, !darkTheme),
    secondary = secondary,
    onSecondary = readableOn(secondary),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = readableOn(secondaryContainer),
    tertiary = tertiary,
    onTertiary = readableOn(tertiary),
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = readableOn(tertiaryContainer),
  )
}

private fun accent(seed: Int, darkTheme: Boolean): Color {
  val hsl = FloatArray(3)
  ColorUtils.colorToHSL(seed, hsl)
  hsl[1] = hsl[1].coerceIn(0.35f, 0.75f)
  hsl[2] = if (darkTheme) 0.72f else 0.36f
  return Color(ColorUtils.HSLToColor(hsl))
}

private fun accentContainer(seed: Int, darkTheme: Boolean): Color {
  val hsl = FloatArray(3)
  ColorUtils.colorToHSL(seed, hsl)
  hsl[1] = hsl[1].coerceIn(0.30f, 0.65f)
  hsl[2] = if (darkTheme) 0.26f else 0.88f
  return Color(ColorUtils.HSLToColor(hsl))
}

private fun readableOn(color: Color): Color {
  val white = ColorUtils.calculateContrast(android.graphics.Color.WHITE, color.toArgb())
  val black = ColorUtils.calculateContrast(android.graphics.Color.BLACK, color.toArgb())
  return if (black >= white) Color.Black else Color.White
}
