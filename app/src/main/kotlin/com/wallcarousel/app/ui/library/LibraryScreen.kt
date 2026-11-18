// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.Carousel
import com.wallcarousel.app.data.model.Wallpaper
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.ui.components.DragSelectOverlay
import com.wallcarousel.app.ui.components.EmptyState
import com.wallcarousel.app.ui.components.ScreenHeader
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
      if (selectionMode) {
        ScreenHeader(
          title = pluralStringResource(
            R.plurals.selected_count,
            selectedIds.size,
            selectedIds.size,
          ),
          actions = {
            TextButton(
              onClick = {
                selectedIds = if (allSelected) emptySet() else allIds.toSet()
              },
            ) {
              Text(
                stringResource(
                  if (allSelected) R.string.deselect_all else R.string.select_all,
                ),
              )
            }
            IconButton(onClick = { showDeleteSelected = true }) {
              Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
            }
            IconButton(onClick = { showAddToCarousel = true }) {
              Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_carousel))
            }
            IconButton(onClick = { selectedIds = emptySet() }) {
              Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
          },
        )
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                  if (selectionMode) {
                    selectedIds = if (wallpaper.id in selectedIds) {
                      selectedIds - wallpaper.id
                    } else {
                      selectedIds + wallpaper.id
                    }
                  } else {
                    selectedId = wallpaper.id
                  }
                },
                onLongClick = { selectedIds = selectedIds + wallpaper.id },
              )
            }
          }
          if (selectionMode) {
            DragSelectOverlay(
              modifier = Modifier.fillMaxSize(),
              keyAt = { position -> gridKeyAt(gridState, position) },
              onSelect = { id ->
                if (state.wallpaper(id) != null) selectedIds = selectedIds + id
              },
              onTap = { id ->
                if (state.wallpaper(id) != null) {
                  selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                }
              },
              onEdgeScroll = { dy ->
                gridState.dispatchRawDelta(dy)
              },
            )
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
      carousels = state.carousels,
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
  onLongClick: () -> Unit,
) {
  val variant = wallpaper.previewVariant()
  val resolution = variant?.let { "${it.width}×${it.height}" }

  Card(
    modifier = Modifier
      .clip(MaterialTheme.shapes.medium)
      .semantics { this.selected = selected }
      .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
  carousels: List<Carousel>,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_to_carousel)) },
    text = {
      if (carousels.isEmpty()) {
        Text(stringResource(R.string.no_carousels_body))
      } else {
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
          lazyItems(carousels, key = { it.id }) { carousel ->
            TextButton(
              onClick = { onSelect(carousel.id) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = carousel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
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
