// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.wallcarousel.app.data.model.ThemeMode

internal val DarkColorScheme = darkColorScheme(
  primary = primaryDark,
  primaryContainer = primaryContainerDark,
  secondary = secondaryDark,
  tertiary = tertiaryDark,
)

internal val LightColorScheme = lightColorScheme(
  primary = primaryLight,
  primaryContainer = primaryContainerLight,
  secondary = secondaryLight,
  tertiary = tertiaryLight,
)

@Composable
fun WallCarouselTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = true,
  wallpaperPalette: Boolean = false,
  paletteSource: WallpaperPaletteSource? = null,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  val colorScheme = when {
    wallpaperPalette -> rememberWallpaperColorScheme(darkTheme, paletteSource)
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
