// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.ui.carousel.CarouselListScreen
import com.wallcarousel.app.ui.library.LibraryScreen
import com.wallcarousel.app.ui.settings.SettingsScreen

enum class AppSection(
  @param:StringRes val labelRes: Int,
  val icon: ImageVector,
) {
  LIBRARY(R.string.nav_wallpapers, Icons.AutoMirrored.Filled.List),
  CAROUSELS(R.string.nav_carousels, Icons.Default.Refresh),
  SETTINGS(R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun WallCarouselRoot(viewModel: AppViewModel) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val message by viewModel.message.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val resources = LocalResources.current
  var section by rememberSaveable { mutableStateOf(AppSection.LIBRARY) }
  var editorActive by remember { mutableStateOf(false) }
  var editWallpaperId by rememberSaveable { mutableStateOf<String?>(null) }
  var editReturnCarouselId by remember { mutableStateOf<String?>(null) }
  var openCarouselId by rememberSaveable { mutableStateOf<String?>(null) }
  var editReturnSection by remember { mutableStateOf<AppSection?>(null) }

  LaunchedEffect(message, resources) {
    val current = message ?: return@LaunchedEffect
    val text = when (current) {
      is UiMessage.Imported -> resources.getQuantityString(
        R.plurals.imported_count,
        current.count,
        current.count,
      )
      UiMessage.ImportFailed -> resources.getString(R.string.import_failed)
      is UiMessage.Applied -> resources.getString(
        if (current.target == ApplyTarget.LOCK) R.string.apply_lock_success else R.string.apply_home_success,
      )
      UiMessage.ApplyFailed -> resources.getString(R.string.apply_failed)
      UiMessage.ApplyUnavailable -> resources.getString(R.string.apply_unavailable)
      UiMessage.NotSupported -> resources.getString(R.string.wallpaper_not_supported)
      UiMessage.Exported -> resources.getString(R.string.export_success)
      UiMessage.ExportFailed -> resources.getString(R.string.export_failed)
      UiMessage.DataDeleted -> resources.getString(R.string.data_deleted)
    }
    viewModel.clearMessage()
    snackbarHostState.showSnackbar(text)
  }

  val loaded = state
  if (loaded == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val expanded = maxWidth >= 600.dp
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!expanded && !editorActive) {
          NavigationBar {
            AppSection.entries.forEach { item ->
              NavigationBarItem(
                selected = section == item,
                onClick = { section = item },
                icon = { Icon(item) },
                label = { Text(stringResource(item.labelRes)) },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (expanded && !editorActive) {
          NavigationRail {
            Spacer(Modifier.height(8.dp))
            AppSection.entries.forEach { item ->
              NavigationRailItem(
                selected = section == item,
                onClick = { section = item },
                icon = { Icon(item) },
                label = { Text(stringResource(item.labelRes)) },
              )
            }
          }
        }
        Box(modifier = Modifier.weight(1f)) {
          when (section) {
            AppSection.LIBRARY -> LibraryScreen(
              state = loaded,
              viewModel = viewModel,
              onEditorActive = { active ->
                val wasActive = editorActive
                editorActive = active
                if (wasActive && !active) {
                  val target = editReturnSection
                  if (target != null) {
                    editReturnSection = null
                    if (target == AppSection.CAROUSELS) {
                      openCarouselId = editReturnCarouselId
                      editReturnCarouselId = null
                    }
                    section = target
                  }
                }
              },
              openWallpaperId = editWallpaperId,
              onOpenConsumed = { editWallpaperId = null },
            )
            AppSection.CAROUSELS -> CarouselListScreen(
              state = loaded,
              viewModel = viewModel,
              onEditWallpaper = { wallpaperId, carouselId ->
                editWallpaperId = wallpaperId
                editReturnCarouselId = carouselId
                editReturnSection = AppSection.CAROUSELS
                section = AppSection.LIBRARY
              },
              openCarouselId = openCarouselId,
              onOpenConsumed = { openCarouselId = null },
            )
            AppSection.SETTINGS -> SettingsScreen(state = loaded, viewModel = viewModel)
          }
          if (busy) {
            LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
          }
        }
      }
    }
  }
}

@Composable
private fun Icon(section: AppSection) {
  androidx.compose.material3.Icon(
    imageVector = section.icon,
    contentDescription = null,
  )
}
