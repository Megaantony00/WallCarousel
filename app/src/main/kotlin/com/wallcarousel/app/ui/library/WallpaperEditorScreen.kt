// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.library

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.Orientation
import com.wallcarousel.app.data.model.Variant
import com.wallcarousel.app.data.model.Wallpaper
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.wallpaper.WallpaperRenderer
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun WallpaperEditorScreen(
  state: AppState,
  wallpaper: Wallpaper,
  viewModel: AppViewModel,
  onBack: () -> Unit,
) {
  var orientationName by rememberSaveable(wallpaper.id) {
    mutableStateOf((wallpaper.availableOrientations().firstOrNull() ?: Orientation.PORTRAIT).name)
  }
  val orientation = Orientation.entries.firstOrNull { it.name == orientationName } ?: Orientation.PORTRAIT
  val stored = wallpaper.variant(orientation)
  var draft by rememberSaveable(wallpaper.id, orientation) { mutableStateOf(stored) }
  LaunchedEffect(stored) {
    if (stored != null) draft = stored
  }

  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  val addLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) viewModel.addVariant(wallpaper.id, orientation, uri)
  }
  val launchAdd = {
    addLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  val save: () -> Unit = { draft?.let { viewModel.updateVariant(wallpaper.id, orientation, it) } }
  val onFocalDelta: (Float, Float) -> Unit = { dx, dy ->
    draft = draft?.let {
      it.copy(
        focalX = (it.focalX + dx).coerceIn(0f, 1f),
        focalY = (it.focalY + dy).coerceIn(0f, 1f),
      )
    }
  }
  val onDragEnd: () -> Unit = { draft?.let { viewModel.updateVariant(wallpaper.id, orientation, it) } }

  BackHandler(onBack = onBack)

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
      }
      Text(
        text = wallpaper.name,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      )
      IconButton(onClick = { confirmDelete = true }) {
        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_wallpaper))
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val wide = maxWidth >= 840.dp
      val aspect = if (orientation == Orientation.PORTRAIT) 3f / 4f else 16f / 10f
      val previewFile = draft?.let { viewModel.imageFile(it.fileName) }

      if (wide) {
        Row(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
          WallpaperPreview(
            file = previewFile,
            variant = draft,
            aspectRatio = aspect,
            onFocalDelta = onFocalDelta,
            onDragEnd = onDragEnd,
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
          Column(
            modifier = Modifier
              .width(380.dp)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
          ) {
            EditorControls(
              orientation = orientation,
              stored = stored,
              draft = draft,
              onOrientationChange = { orientationName = it.name },
              onDraftChange = { draft = it },
              onSave = save,
              onAddVariant = launchAdd,
            )
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          WallpaperPreview(
            file = previewFile,
            variant = draft,
            aspectRatio = aspect,
            onFocalDelta = onFocalDelta,
            onDragEnd = onDragEnd,
            modifier = Modifier.fillMaxWidth(),
          )
          EditorControls(
            orientation = orientation,
            stored = stored,
            draft = draft,
            onOrientationChange = { orientationName = it.name },
            onDraftChange = { draft = it },
            onSave = save,
            onAddVariant = launchAdd,
          )
        }
      }
    }
  }

  if (confirmDelete) {
    ConfirmDialog(
      title = stringResource(R.string.delete_wallpaper),
      body = stringResource(R.string.delete_wallpaper_body),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        viewModel.deleteWallpaper(wallpaper.id)
        onBack()
      },
      onDismiss = { confirmDelete = false },
    )
  }
}

@Composable
private fun EditorControls(
  orientation: Orientation,
  stored: Variant?,
  draft: Variant?,
  onOrientationChange: (Orientation) -> Unit,
  onDraftChange: (Variant) -> Unit,
  onSave: () -> Unit,
  onAddVariant: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Orientation.entries.forEach { item ->
        FilterChip(
          selected = orientation == item,
          onClick = { onOrientationChange(item) },
          label = { Text(stringResource(orientationLabel(item))) },
          modifier = Modifier.heightIn(min = 48.dp),
        )
      }
    }

    if (draft == null) {
      Card {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(
              R.string.missing_orientation_title,
              stringResource(orientationLabel(orientation)),
            ),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = stringResource(R.string.missing_orientation_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = onAddVariant) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
              text = stringResource(
                R.string.add_orientation_image,
                stringResource(orientationLabel(orientation)),
              ),
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }
      return@Column
    }

    val current = draft
    Text(
      text = stringResource(R.string.drag_focus_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    AdjustmentSlider(
      label = stringResource(R.string.zoom),
      valueLabel = String.format(Locale.getDefault(), "%.1fx", current.zoom),
      value = current.zoom,
      range = 1f..3f,
      onValueChange = { onDraftChange(current.copy(zoom = it)) },
      onValueChangeFinished = onSave,
    )
    AdjustmentSlider(
      label = stringResource(R.string.brightness),
      valueLabel = "${(current.brightness * 100).roundToInt()}%",
      value = current.brightness,
      range = -0.5f..0.5f,
      onValueChange = { onDraftChange(current.copy(brightness = it)) },
      onValueChangeFinished = onSave,
    )
    AdjustmentSlider(
      label = stringResource(R.string.contrast),
      valueLabel = "${(current.contrast * 100).roundToInt()}%",
      value = current.contrast,
      range = 0.5f..1.5f,
      onValueChange = { onDraftChange(current.copy(contrast = it)) },
      onValueChangeFinished = onSave,
    )
    AdjustmentSlider(
      label = stringResource(R.string.saturation),
      valueLabel = "${(current.saturation * 100).roundToInt()}%",
      value = current.saturation,
      range = 0f..2f,
      onValueChange = { onDraftChange(current.copy(saturation = it)) },
      onValueChangeFinished = onSave,
    )
    AdjustmentSlider(
      label = stringResource(R.string.blur),
      valueLabel = current.blur.toString(),
      value = current.blur.toFloat(),
      range = 0f..25f,
      steps = 24,
      onValueChange = { onDraftChange(current.copy(blur = it.roundToInt())) },
      onValueChangeFinished = onSave,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = {
          if (stored != null) {
            onDraftChange(
              Variant(
                fileName = stored.fileName,
                width = stored.width,
                height = stored.height,
              ),
            )
            onSave()
          }
        },
      ) {
        Text(stringResource(R.string.reset_adjustments))
      }
    }
  }
}

@Composable
private fun AdjustmentSlider(
  label: String,
  valueLabel: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  steps: Int = 0,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
) {
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium)
      Text(
        text = valueLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = range,
      steps = steps,
      onValueChangeFinished = onValueChangeFinished,
    )
  }
}

@Composable
private fun WallpaperPreview(
  file: File?,
  variant: Variant?,
  aspectRatio: Float,
  onFocalDelta: (Float, Float) -> Unit,
  onDragEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    val maxPreviewWidth = if (aspectRatio < 1f) 380.dp else maxWidth
    val previewWidth = minOf(maxWidth, maxPreviewWidth)
    val density = LocalDensity.current
    val widthPx = with(density) { previewWidth.roundToPx() }.coerceAtLeast(1)
    val renderWidth = widthPx.coerceAtMost(900)

    Box(
      modifier = Modifier
        .width(previewWidth)
        .aspectRatio(aspectRatio)
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
      if (file != null && variant != null) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(file, variant, renderWidth, aspectRatio) {
          delay(100)
          bitmap = withContext(Dispatchers.Default) {
            WallpaperRenderer.render(
              file = file,
              variant = variant,
              targetWidth = renderWidth,
              targetHeight = (renderWidth / aspectRatio).roundToInt().coerceAtLeast(1),
            )
          }
        }
        val rendered = bitmap
        if (rendered != null) {
          Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = stringResource(R.string.wallpaper_preview),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }

        val latestDelta by rememberUpdatedState(onFocalDelta)
        val latestEnd by rememberUpdatedState(onDragEnd)
        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(file, aspectRatio) {
              detectDragGestures(
                onDragEnd = { latestEnd() },
                onDrag = { change, dragAmount ->
                  change.consume()
                  latestDelta(-dragAmount.x / size.width, -dragAmount.y / size.height)
                },
              )
            },
        )
      } else {
        Text(
          text = stringResource(R.string.no_preview),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.align(Alignment.Center),
        )
      }
    }
  }
}

private fun orientationLabel(orientation: Orientation): Int = when (orientation) {
  Orientation.PORTRAIT -> R.string.orientation_portrait
  Orientation.LANDSCAPE -> R.string.orientation_landscape
}
