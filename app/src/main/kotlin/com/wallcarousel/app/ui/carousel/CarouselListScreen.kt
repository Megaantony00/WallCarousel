// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.carousel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.Carousel
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.EmptyState
import com.wallcarousel.app.ui.components.ScreenHeader
import com.wallcarousel.app.ui.components.rememberThumbnail
import java.io.File

@Composable
fun CarouselListScreen(state: AppState, viewModel: AppViewModel) {
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  val selected = state.carousel(selectedId)

  if (selected != null) {
    BackHandler { selectedId = null }
    CarouselEditorScreen(
      state = state,
      carousel = selected,
      viewModel = viewModel,
      onBack = { selectedId = null },
    )
    return
  }

  var showCreate by rememberSaveable { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
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
        LazyColumn(
          contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(items = state.carousels, key = { it.id }) { carousel ->
            CarouselCard(
              carousel = carousel,
              state = state,
              imageFile = viewModel::imageFile,
              onClick = { selectedId = carousel.id },
            )
          }
        }
      }
    }

    if (state.carousels.isNotEmpty()) {
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
}

@Composable
private fun CarouselCard(
  carousel: Carousel,
  state: AppState,
  imageFile: (String) -> File,
  onClick: () -> Unit,
) {
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
    }
  }
}

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
