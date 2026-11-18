// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.carousel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.ui.components.EmptyState
import com.wallcarousel.app.ui.components.ScreenHeader
import com.wallcarousel.app.ui.components.SelectionGestureLayer
import com.wallcarousel.app.ui.components.SelectionSetSaver
import com.wallcarousel.app.ui.components.rememberThumbnail
import java.io.File

@Composable
fun CarouselListScreen(
  state: AppState,
  viewModel: AppViewModel,
  onEditWallpaper: (String, String) -> Unit,
  openCarouselId: String? = null,
  onOpenConsumed: () -> Unit = {},
) {
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedIds by rememberSaveable(stateSaver = SelectionSetSaver) {
    mutableStateOf(emptySet())
  }
  var showDeleteSelected by rememberSaveable { mutableStateOf(false) }
  val selected = state.carousel(selectedId)
  val selectionMode = selectedIds.isNotEmpty()
  val allIds = state.carousels.map { it.id }
  val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
  val listState = rememberLazyListState()
  val selectedCarousel = if (selectedIds.size == 1) {
    state.carousels.firstOrNull { it.id in selectedIds }
  } else {
    null
  }
  val selectedIndex = selectedCarousel?.let { state.carousels.indexOf(it) } ?: -1
  val canMoveUp = selectedCarousel != null && selectedIndex > 0
  val canMoveDown = selectedCarousel != null && selectedIndex in 0 until state.carousels.lastIndex

  LaunchedEffect(openCarouselId) {
    if (openCarouselId != null) {
      selectedIds = emptySet()
      selectedId = openCarouselId
      onOpenConsumed()
    }
  }
  BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

  if (selected != null) {
    BackHandler { selectedId = null }
    CarouselEditorScreen(
      state = state,
      carousel = selected,
      viewModel = viewModel,
      onBack = { selectedId = null },
      onEditWallpaper = { wallpaperId -> onEditWallpaper(wallpaperId, selected.id) },
    )
    return
  }

  var showCreate by rememberSaveable { mutableStateOf(false) }

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
              Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
            }
            IconButton(
              onClick = { selectedCarousel?.let { viewModel.moveCarousel(it.id, up = true) } },
              enabled = canMoveUp,
            ) {
              Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
            }
            IconButton(
              onClick = { selectedCarousel?.let { viewModel.moveCarousel(it.id, up = false) } },
              enabled = canMoveDown,
            ) {
              Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.move_down),
              )
            }
            IconButton(onClick = { showDeleteSelected = true }) {
              Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
            }
            IconButton(onClick = { selectedIds = emptySet() }) {
              Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
          },
        )
      } else {
        ScreenHeader(
          title = stringResource(R.string.carousels_title),
          subtitle = if (state.carousels.isEmpty()) {
            null
          } else {
            pluralStringResource(
              R.plurals.carousels_count,
              state.carousels.size,
              state.carousels.size,
            )
          },
        )
      }

      if (state.carousels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          EmptyState(
            title = stringResource(R.string.carousels_empty_title),
            body = stringResource(R.string.carousels_empty_body),
            actionLabel = stringResource(R.string.new_carousel),
            onAction = { showCreate = true },
          )
        }
      } else {
        SelectionGestureLayer(
          selectionMode = selectionMode,
          modifier = Modifier.fillMaxSize(),
          keyAt = { position -> carouselListKeyAt(listState, position) },
          onSelect = { id ->
            if (state.carousel(id) != null) selectedIds = selectedIds + id
          },
          onTap = { id ->
            if (state.carousel(id) != null) {
              selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            }
          },
          onEdgeScroll = { dy ->
            listState.dispatchRawDelta(dy)
          },
        ) {
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(items = state.carousels, key = { it.id }) { carousel ->
              CarouselCard(
                carousel = carousel,
                state = state,
                imageFile = viewModel::imageFile,
                selected = carousel.id in selectedIds,
                onClick = { if (!selectionMode) selectedId = carousel.id },
              )
            }
          }
        }
      }
    }

    if (state.carousels.isNotEmpty() && !selectionMode) {
      ExtendedFloatingActionButton(
        onClick = { showCreate = true },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.new_carousel)) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
      )
    }
  }

  if (showCreate) {
    CreateCarouselDialog(
      onDismiss = { showCreate = false },
      onCreate = { name ->
        viewModel.createCarousel(name)
        showCreate = false
      },
    )
  }

  if (showDeleteSelected) {
    ConfirmDialog(
      title = stringResource(R.string.delete_selected),
      body = pluralStringResource(
        R.plurals.delete_selected_carousels_body,
        selectedIds.size,
        selectedIds.size,
      ),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        viewModel.deleteCarousels(selectedIds)
        selectedIds = emptySet()
        showDeleteSelected = false
      },
      onDismiss = { showDeleteSelected = false },
    )
  }
}

@Composable
private fun CarouselCard(
  carousel: Carousel,
  state: AppState,
  imageFile: (String) -> File,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .semantics { this.selected = selected }
      .clickable(onClick = onClick),
    colors = if (selected) {
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
      CardDefaults.cardColors()
    },
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        val first = carousel.wallpaperIds.firstOrNull()?.let(state::wallpaper)?.previewVariant()
        val bitmap = rememberThumbnail(first?.let { imageFile(it.fileName) }, 240)
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = carousel.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = pluralStringResource(
            R.plurals.wallpapers_in_carousel,
            state.wallpaperCount(carousel),
            state.wallpaperCount(carousel),
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (state.home.carouselId == carousel.id) {
            AssignmentBadge(stringResource(R.string.home_screen))
          }
          if (state.lock.carouselId == carousel.id) {
            AssignmentBadge(stringResource(R.string.lock_screen))
          }
        }
      }
      if (selected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

private fun carouselListKeyAt(listState: LazyListState, position: Offset): String? =
  listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
    position.y >= item.offset.toFloat() &&
      position.y <= (item.offset + item.size).toFloat()
  }?.key as? String

@Composable
private fun AssignmentBadge(text: String) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.small,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun CreateCarouselDialog(
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.new_carousel)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.carousel_name)) },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onCreate(name) },
        enabled = name.isNotBlank(),
      ) {
        Text(stringResource(R.string.create))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}
