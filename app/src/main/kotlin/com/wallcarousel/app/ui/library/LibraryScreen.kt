// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.Wallpaper
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.EmptyState
import com.wallcarousel.app.ui.components.ScreenHeader
import com.wallcarousel.app.ui.components.rememberThumbnail

@Composable
fun LibraryScreen(state: AppState, viewModel: AppViewModel) {
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  val selected = state.wallpaper(selectedId)

  if (selected != null) {
    androidx.activity.compose.BackHandler { selectedId = null }
    WallpaperEditorScreen(
      state = state,
      wallpaper = selected,
      viewModel = viewModel,
      onBack = { selectedId = null },
    )
    return
  }

  val picker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(100),
  ) { uris ->
    if (uris.isNotEmpty()) viewModel.importImages(uris)
  }
  val launchPicker = {
    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      ScreenHeader(
        title = stringResource(R.string.wallpapers_title),
        subtitle = if (state.wallpapers.isEmpty()) {
          null
        } else {
          pluralStringResource(
            R.plurals.wallpapers_count,
            state.wallpapers.size,
            state.wallpapers.size,
          )
        },
      )

      if (state.wallpapers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          EmptyState(
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
            actionLabel = stringResource(R.string.import_wallpapers),
            onAction = launchPicker,
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 180.dp),
          contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(items = state.wallpapers, key = { it.id }) { wallpaper ->
            WallpaperCard(
              wallpaper = wallpaper,
              imageFile = viewModel::imageFile,
              onClick = { selectedId = wallpaper.id },
            )
          }
        }
      }
    }

    if (state.wallpapers.isNotEmpty()) {
      ExtendedFloatingActionButton(
        onClick = launchPicker,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.import_wallpapers)) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
      )
    }
  }
}

@Composable
private fun WallpaperCard(
  wallpaper: Wallpaper,
  imageFile: (String) -> java.io.File,
  onClick: () -> Unit,
) {
  Card(onClick = onClick) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(4f / 3f)
          .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        val variant = wallpaper.previewVariant()
        val bitmap = rememberThumbnail(
          file = variant?.let { imageFile(it.fileName) },
          maxSize = 480,
        )
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = wallpaper.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
        Row(
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (wallpaper.portrait != null) {
            OrientationBadge(
              text = "P",
              description = stringResource(R.string.orientation_portrait),
            )
          }
          if (wallpaper.landscape != null) {
            OrientationBadge(
              text = "L",
              description = stringResource(R.string.orientation_landscape),
            )
          }
        }
      }
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          text = wallpaper.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = orientationSummary(wallpaper),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun OrientationBadge(text: String, description: String) {
  Surface(
    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
  ) {
    Text(
      text = text,
      fontSize = 11.sp,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun orientationSummary(wallpaper: Wallpaper): String = when {
  wallpaper.portrait != null && wallpaper.landscape != null -> stringResource(R.string.orientation_both)
  wallpaper.portrait != null -> stringResource(R.string.orientation_portrait)
  else -> stringResource(R.string.orientation_landscape)
}
