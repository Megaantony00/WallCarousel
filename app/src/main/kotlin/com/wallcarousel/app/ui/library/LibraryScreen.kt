// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.Wallpaper
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.ui.components.EmptyState
import com.wallcarousel.app.ui.components.ScreenHeader
import com.wallcarousel.app.ui.components.SelectionGestureLayer
import com.wallcarousel.app.ui.components.SelectionSetSaver
import com.wallcarousel.app.ui.components.rememberThumbnail

@Composable
fun LibraryScreen(
  state: AppState,
  viewModel: AppViewModel,
  onEditorActive: (Boolean) -> Unit = {},
  openWallpaperId: String? = null,
  onOpenConsumed: () -> Unit = {},
) {
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedIds by rememberSaveable(stateSaver = SelectionSetSaver) {
    mutableStateOf(emptySet())
  }
  var showDeleteSelected by rememberSaveable { mutableStateOf(false) }
  var showAddToCarousel by rememberSaveable { mutableStateOf(false) }
  val selected = state.wallpaper(selectedId)
  val selectionMode = selectedIds.isNotEmpty()
  val allIds = state.wallpapers.map { it.id }
  val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
  val gridState = rememberLazyGridState()

  LaunchedEffect(openWallpaperId) {
    if (openWallpaperId != null) {
      selectedIds = emptySet()
      selectedId = openWallpaperId
      onOpenConsumed()
    }
  }
  LaunchedEffect(selected != null) { onEditorActive(selected != null) }
  DisposableEffect(Unit) { onDispose { onEditorActive(false) } }
  BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

  if (selected != null) {
    BackHandler { selectedId = null }
    WallpaperEditorScreen(
      state = state,
      wallpaper = selected,
      viewModel = viewModel,
      onBack = { selectedId = null },
      onNavigate = { selectedId = it },
    )
    return
  }

  val picker = rememberLauncherForActivityResult(PickGalleryImages()) { uris ->
    if (uris.isNotEmpty()) viewModel.importImages(uris)
  }
  val launchPicker = { picker.launch(Unit) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (selectionMode) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          TriStateCheckbox(
            state = when {
              allSelected -> ToggleableState.On
              selectedIds.isEmpty() -> ToggleableState.Off
              else -> ToggleableState.Indeterminate
            },
            onClick = {
              selectedIds = if (allSelected) emptySet() else allIds.toSet()
            },
          )
          Text(
            text = pluralStringResource(
              R.plurals.selected_count,
              selectedIds.size,
              selectedIds.size,
            ),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { showAddToCarousel = true }) {
            Text(stringResource(R.string.add_to_carousel))
          }
          IconButton(onClick = { showDeleteSelected = true }) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
          }
        }
      } else {
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
      }

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
        SelectionGestureLayer(
          selectionMode = selectionMode,
          modifier = Modifier.fillMaxSize(),
          keyAt = { position -> gridKeyAt(gridState, position) },
          onSelect = { id ->
            if (state.wallpaper(id) != null) selectedIds = selectedIds + id
          },
          onDragItem = { id ->
            if (state.wallpaper(id) != null) {
              selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            }
          },
          onTap = { id ->
            if (state.wallpaper(id) != null) {
              selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            }
          },
          onEdgeScroll = { dy ->
            gridState.dispatchRawDelta(dy)
          },
        ) {
          LazyVerticalGrid(
            state = gridState,
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
                selected = wallpaper.id in selectedIds,
                onClick = {
                  if (!selectionMode) selectedId = wallpaper.id
                },
              )
            }
          }
        }
      }
    }

    if (state.wallpapers.isNotEmpty() && !selectionMode) {
      ExtendedFloatingActionButton(
        onClick = launchPicker,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.import_wallpapers)) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
      )
    }
  }

  if (showDeleteSelected) {
    ConfirmDialog(
      title = stringResource(R.string.delete_selected),
      body = pluralStringResource(
        R.plurals.delete_selected_body,
        selectedIds.size,
        selectedIds.size,
      ),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        viewModel.deleteWallpapers(selectedIds)
        selectedIds = emptySet()
        showDeleteSelected = false
      },
      onDismiss = { showDeleteSelected = false },
    )
  }

  if (showAddToCarousel) {
    AddToCarouselDialog(
      state = state,
      selectedIds = selectedIds,
      imageFile = viewModel::imageFile,
      onDismiss = { showAddToCarousel = false },
      onSelect = { carouselId ->
        viewModel.addWallpapersToCarousel(carouselId, selectedIds.toList())
        selectedIds = emptySet()
        showAddToCarousel = false
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperCard(
  wallpaper: Wallpaper,
  imageFile: (String) -> java.io.File,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val variant = wallpaper.previewVariant()
  val resolution = variant?.let { "${it.width}×${it.height}" }

  Card(
    modifier = Modifier
      .clip(MaterialTheme.shapes.medium)
      .semantics { this.selected = selected }
      .clickable(onClick = onClick),
  ) {
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
      if (selected) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
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
          text = wallpaper.displayName(),
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
          overflow = TextOverflow.Clip,
          modifier = Modifier.weight(1f).basicMarquee(),
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
      if (selected) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.padding(5.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun AddToCarouselDialog(
  state: AppState,
  selectedIds: Set<String>,
  imageFile: (String) -> java.io.File,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_to_carousel)) },
    text = {
      if (state.carousels.isEmpty()) {
        Text(stringResource(R.string.no_carousels_body))
      } else {
        LazyColumn(
          modifier = Modifier.heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          lazyItems(state.carousels, key = { it.id }) { carousel ->
            val missing = selectedIds.count { it !in carousel.wallpaperIds }
            val enabled = missing > 0
            val firstVariant = carousel.wallpaperIds
              .firstNotNullOfOrNull { state.wallpaper(it) }
              ?.previewVariant()
            val thumbnail = rememberThumbnail(
              file = firstVariant?.let { imageFile(it.fileName) },
              maxSize = 120,
            )
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .alpha(if (enabled) 1f else 0.55f)
                .clickable(enabled = enabled) { onSelect(carousel.id) },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  if (thumbnail != null) {
                    Image(
                      bitmap = thumbnail.asImageBitmap(),
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    Text(
                      text = carousel.name.trim().take(1).uppercase().ifBlank { "?" },
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = carousel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = if (enabled) {
                      stringResource(R.string.to_add_count, missing)
                    } else {
                      stringResource(R.string.all_already_added)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(
                  imageVector = if (enabled) Icons.Default.Add else Icons.Default.Check,
                  contentDescription = null,
                  tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}

private fun gridKeyAt(gridState: LazyGridState, position: Offset): String? =
  gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
    position.x >= item.offset.x.toFloat() &&
      position.x <= (item.offset.x + item.size.width).toFloat() &&
      position.y >= item.offset.y.toFloat() &&
      position.y <= (item.offset.y + item.size.height).toFloat()
  }?.key as? String
