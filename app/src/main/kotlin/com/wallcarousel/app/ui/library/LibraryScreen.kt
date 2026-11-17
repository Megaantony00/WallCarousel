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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun LibraryScreen(
  state: AppState,
  viewModel: AppViewModel,
  onEditorActive: (Boolean) -> Unit = {},
) {
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  val selected = state.wallpaper(selectedId)

  LaunchedEffect(selected != null) { onEditorActive(selected != null) }
  DisposableEffect(Unit) { onDispose { onEditorActive(false) } }

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
  val carouselsByWallpaper = buildMap<String, MutableList<String>> {
    state.carousels.forEach { carousel ->
      carousel.wallpaperIds.forEach { wallpaperId ->
        getOrPut(wallpaperId) { mutableListOf() }.add(carousel.name)
      }
    }
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
              carouselNames = carouselsByWallpaper[wallpaper.id].orEmpty(),
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
  carouselNames: List<String>,
  imageFile: (String) -> java.io.File,
  onClick: () -> Unit,
) {
  val variant = wallpaper.previewVariant()
  val resolution = variant?.let { "${it.width}×${it.height}" }
  val carouselLabel = when {
    carouselNames.isEmpty() -> stringResource(R.string.wallpaper_not_in_carousel)
    carouselNames.size == 1 -> carouselNames.first()
    else -> stringResource(
      R.string.wallpaper_carousel_more,
      carouselNames.first(),
      carouselNames.size - 1,
    )
  }

  Card(onClick = onClick) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(4f / 3f)
        .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
      val bitmap = rememberThumbnail(
        file = variant?.let { imageFile(it.fileName) },
        maxSize = 480,
      )
      if (bitmap != null) {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = wallpaper.displayName(),
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
      Row(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = carouselLabel,
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (resolution != null) {
          Text(
            text = resolution,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun OrientationBadge(text: String, description: String) {
  Surface(
    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
    contentColor = Color.White,
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
