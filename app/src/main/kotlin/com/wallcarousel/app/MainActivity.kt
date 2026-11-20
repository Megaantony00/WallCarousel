// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallcarousel.app.data.model.ThemeMode
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.WallCarouselRoot
import com.wallcarousel.app.ui.theme.WallCarouselTheme
import com.wallcarousel.app.ui.theme.WallpaperPaletteSource

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppLanguage.wrap(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: AppViewModel = viewModel()
      val state by viewModel.state.collectAsStateWithLifecycle()
      val paletteSource = state?.let { current ->
        current.currentVariant(current.wallpaperPaletteTarget)?.let { variant ->
          WallpaperPaletteSource(viewModel.imageFile(variant.fileName), variant)
        }
      }
      WallCarouselTheme(
        themeMode = state?.themeMode ?: ThemeMode.SYSTEM,
        dynamicColor = state?.dynamicColor ?: true,
        wallpaperPalette = state?.wallpaperPalette ?: false,
        paletteSource = paletteSource,
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          WallCarouselRoot(viewModel)
        }
      }
    }
  }
}
