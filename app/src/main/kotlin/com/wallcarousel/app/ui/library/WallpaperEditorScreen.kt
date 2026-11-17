// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.library

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.CropMath
import com.wallcarousel.app.data.model.Orientation
import com.wallcarousel.app.data.model.Variant
import com.wallcarousel.app.data.model.Wallpaper
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.wallpaper.ThumbnailLoader
import com.wallcarousel.app.wallpaper.WallpaperRenderer
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface PendingChange {
  data object Back : PendingChange
  data class OrientationChange(val value: Orientation) : PendingChange
}

@Composable
fun WallpaperEditorScreen(
  state: AppState,
  wallpaper: Wallpaper,
  viewModel: AppViewModel,
  onBack: () -> Unit,
) {
  var orientationName by rememberSaveable(wallpaper.id) {
    val natural = when {
      wallpaper.portrait == null && wallpaper.landscape != null -> Orientation.LANDSCAPE
      wallpaper.landscape == null && wallpaper.portrait != null -> Orientation.PORTRAIT
      else -> {
        val variant = wallpaper.previewVariant()
        if (variant != null && variant.width > variant.height) {
          Orientation.LANDSCAPE
        } else {
          Orientation.PORTRAIT
        }
      }
    }
    mutableStateOf(natural.name)
  }
  val orientation = Orientation.entries.firstOrNull { it.name == orientationName } ?: Orientation.PORTRAIT
  val stored = wallpaper.variant(orientation)
  var draft by rememberSaveable(wallpaper.id, orientation) { mutableStateOf(stored) }
  LaunchedEffect(stored) {
    if (stored != null) draft = stored
  }
  var draftName by rememberSaveable(wallpaper.id) { mutableStateOf(wallpaper.name) }
  LaunchedEffect(wallpaper.name) { draftName = wallpaper.name }

  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var showGrid by rememberSaveable(wallpaper.id) { mutableStateOf(false) }
  var pendingChange by remember { mutableStateOf<PendingChange?>(null) }
  val addLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) viewModel.addVariant(wallpaper.id, orientation, uri)
  }
  val launchAdd = {
    addLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  val dirty = draft != stored || draftName.trim() != wallpaper.name
  val namePlaceholder = wallpaper.sourceName.ifBlank {
    stringResource(R.string.wallpaper_default_name)
  }
  val saveAll: () -> Unit = {
    if (draft != null || draftName.trim() != wallpaper.name) {
      viewModel.updateWallpaper(wallpaper.id, draftName, orientation, draft)
    }
  }
  val applyPending: (PendingChange) -> Unit = { change ->
    when (change) {
      PendingChange.Back -> onBack()
      is PendingChange.OrientationChange -> orientationName = change.value.name
    }
  }
  val requestBack: () -> Unit = {
    if (dirty) pendingChange = PendingChange.Back else onBack()
  }
  val requestOrientation: (Orientation) -> Unit = { value ->
    if (value != orientation) {
      if (dirty) pendingChange = PendingChange.OrientationChange(value) else orientationName = value.name
    }
  }
  val onTransformEnd: (Float, Float, Float) -> Unit = { focalX, focalY, zoom ->
    draft = draft?.copy(focalX = focalX, focalY = focalY, zoom = zoom)
  }

  BackHandler(onBack = requestBack)

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = requestBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
      }
      Text(
        text = wallpaper.displayName(),
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
            showGrid = showGrid,
            onToggleGrid = { showGrid = !showGrid },
            onTransformEnd = onTransformEnd,
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
              name = draftName,
              namePlaceholder = namePlaceholder,
              onNameChange = { draftName = it },
              onOrientationChange = requestOrientation,
              onDraftChange = { draft = it },
              onSave = saveAll,
              canSave = dirty,
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
            showGrid = showGrid,
            onToggleGrid = { showGrid = !showGrid },
            onTransformEnd = onTransformEnd,
            modifier = Modifier.fillMaxWidth(),
          )
          EditorControls(
            orientation = orientation,
            stored = stored,
            draft = draft,
            name = draftName,
            namePlaceholder = namePlaceholder,
            onNameChange = { draftName = it },
            onOrientationChange = requestOrientation,
            onDraftChange = { draft = it },
            onSave = saveAll,
            canSave = dirty,
            onAddVariant = launchAdd,
          )
        }
      }
    }
  }

  pendingChange?.let { change ->
    AlertDialog(
      onDismissRequest = { pendingChange = null },
      title = { Text(stringResource(R.string.unsaved_changes_title)) },
      text = { Text(stringResource(R.string.unsaved_changes_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            saveAll()
            pendingChange = null
            applyPending(change)
          },
        ) {
          Text(stringResource(R.string.save))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            pendingChange = null
            if (change is PendingChange.OrientationChange) {
              draftName = wallpaper.name
            }
            applyPending(change)
          },
        ) {
          Text(stringResource(R.string.discard_changes))
        }
      },
    )
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
  name: String,
  namePlaceholder: String,
  onNameChange: (String) -> Unit,
  onOrientationChange: (Orientation) -> Unit,
  onDraftChange: (Variant) -> Unit,
  onSave: () -> Unit,
  canSave: Boolean,
  onAddVariant: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      value = name,
      onValueChange = onNameChange,
      label = { Text(stringResource(R.string.wallpaper_name_label)) },
      supportingText = {
        Text(stringResource(R.string.wallpaper_source_name, namePlaceholder))
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = onSave,
      enabled = canSave,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
      Text(stringResource(R.string.save))
    }

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
    )
    AdjustmentSlider(
      label = stringResource(R.string.brightness),
      valueLabel = "${(current.brightness * 100).roundToInt()}%",
      value = current.brightness,
      range = -0.5f..0.5f,
      onValueChange = { onDraftChange(current.copy(brightness = it)) },
    )
    AdjustmentSlider(
      label = stringResource(R.string.contrast),
      valueLabel = "${(current.contrast * 100).roundToInt()}%",
      value = current.contrast,
      range = 0.5f..1.5f,
      onValueChange = { onDraftChange(current.copy(contrast = it)) },
    )
    AdjustmentSlider(
      label = stringResource(R.string.saturation),
      valueLabel = "${(current.saturation * 100).roundToInt()}%",
      value = current.saturation,
      range = 0f..2f,
      onValueChange = { onDraftChange(current.copy(saturation = it)) },
    )
    AdjustmentSlider(
      label = stringResource(R.string.blur),
      valueLabel = current.blur.toString(),
      value = current.blur.toFloat(),
      range = 0f..25f,
      steps = 24,
      onValueChange = { onDraftChange(current.copy(blur = it.roundToInt())) },
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
    )
  }
}

private const val PREVIEW_MAX_SIZE = 2048

@Composable
private fun WallpaperPreview(
  file: File?,
  variant: Variant?,
  aspectRatio: Float,
  showGrid: Boolean,
  onToggleGrid: () -> Unit,
  onTransformEnd: (Float, Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    val maxPreviewWidth = if (aspectRatio < 1f) 380.dp else maxWidth
    val previewWidth = minOf(maxWidth, maxPreviewWidth)
    val density = LocalDensity.current
    val widthPx = with(density) { previewWidth.roundToPx() }.coerceAtLeast(1)
    val previewDescription = stringResource(R.string.wallpaper_preview)

    Box(
      modifier = Modifier
        .width(previewWidth)
        .aspectRatio(aspectRatio)
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
      if (file != null && variant != null) {
        var source by remember(file) { mutableStateOf<Bitmap?>(null) }
        var loaded by remember(file) { mutableStateOf(false) }
        LaunchedEffect(file) {
          source = ThumbnailLoader.load(file, PREVIEW_MAX_SIZE)
          loaded = true
        }

        val bitmap = source
        if (bitmap != null) {
          // Keep these state objects stable across recompositions. Recreating
          // them after a save would leave the pointer-input coroutine writing
          // to the old objects, which made every gesture after the first one
          // update an invisible state.
          var liveFocalX by remember(file) { mutableFloatStateOf(variant.focalX) }
          var liveFocalY by remember(file) { mutableFloatStateOf(variant.focalY) }
          var liveZoom by remember(file) { mutableFloatStateOf(variant.zoom) }
          LaunchedEffect(variant.focalX, variant.focalY, variant.zoom) {
            liveFocalX = variant.focalX
            liveFocalY = variant.focalY
            liveZoom = variant.zoom
          }
          val latestEnd = rememberUpdatedState(onTransformEnd)
          val colorFilter = remember(variant.brightness, variant.contrast, variant.saturation) {
            WallpaperRenderer.colorFilter(variant)
          }
          val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG) }
          val blurRadius = with(density) {
            (variant.blur * widthPx / 1080f).toDp()
          }
          val imageModifier = if (
            variant.blur > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          ) {
            Modifier.blur(blurRadius)
          } else {
            Modifier
          }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .semantics { contentDescription = previewDescription }
              .pointerInput(bitmap, aspectRatio) {
                awaitEachGesture {
                  if (size.width <= 0 || size.height <= 0) return@awaitEachGesture
                  var pastTouchSlop = false
                  var accumulatedZoom = 1f
                  var accumulatedPan = Offset.Zero
                  val touchSlop = viewConfiguration.touchSlop

                  awaitFirstDown(requireUnconsumed = false)
                  do {
                    val event = awaitPointerEvent()
                    val canceled = event.changes.any { it.isConsumed }
                    if (!canceled) {
                      val zoomChange = event.calculateZoom()
                      val panChange = event.calculatePan()
                      if (!pastTouchSlop) {
                        accumulatedZoom *= zoomChange
                        accumulatedPan += panChange
                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                        val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                        val panMotion = accumulatedPan.getDistance()
                        if (zoomMotion > touchSlop || panMotion > touchSlop) {
                          pastTouchSlop = true
                        }
                      }
                      if (pastTouchSlop) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                          val centroid = event.calculateCentroid(useCurrent = false)
                          val result = CropMath.transform(
                            sourceWidth = bitmap.width,
                            sourceHeight = bitmap.height,
                            targetAspect = aspectRatio,
                            focalX = liveFocalX,
                            focalY = liveFocalY,
                            zoom = liveZoom,
                            centroidX = centroid.x / size.width,
                            centroidY = centroid.y / size.height,
                            panXFraction = panChange.x / size.width,
                            panYFraction = panChange.y / size.height,
                            zoomChange = zoomChange,
                          )
                          liveFocalX = result.focalX
                          liveFocalY = result.focalY
                          liveZoom = result.zoom
                        }
                        event.changes.forEach { change ->
                          if (change.positionChanged()) change.consume()
                        }
                      }
                    }
                  } while (!canceled && event.changes.any { it.pressed })

                  if (pastTouchSlop) {
                    latestEnd.value(liveFocalX, liveFocalY, liveZoom)
                  }
                }
              },
          ) {
            Box(modifier = Modifier.fillMaxSize().then(imageModifier)) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val crop = CropMath.cropRect(
                  sourceWidth = bitmap.width,
                  sourceHeight = bitmap.height,
                  targetAspect = aspectRatio,
                  focalX = liveFocalX,
                  focalY = liveFocalY,
                  zoom = liveZoom,
                )
                val left = crop.left.roundToInt().coerceIn(0, bitmap.width - 1)
                val top = crop.top.roundToInt().coerceIn(0, bitmap.height - 1)
                val right = crop.right.roundToInt().coerceIn(left + 1, bitmap.width)
                val bottom = crop.bottom.roundToInt().coerceIn(top + 1, bitmap.height)
                paint.colorFilter = colorFilter
                drawIntoCanvas { canvas ->
                  canvas.nativeCanvas.drawBitmap(
                    bitmap,
                    Rect(left, top, right, bottom),
                    RectF(0f, 0f, size.width, size.height),
                    paint,
                  )
                }
              }
            }
          }

          if (showGrid) {
            GridOverlay(modifier = Modifier.fillMaxSize())
          }

          FilledTonalIconButton(
            onClick = onToggleGrid,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(12.dp)
              .size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Icon(
              imageVector = GridIcon,
              contentDescription = stringResource(
                if (showGrid) R.string.hide_grid else R.string.show_grid,
              ),
            )
          }
        } else if (loaded) {
          Text(
            text = stringResource(R.string.no_preview),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
          )
        } else {
          CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(32.dp),
            strokeWidth = 3.dp,
          )
        }
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

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val line = 1.dp.toPx()
    for (index in 1..2) {
      val x = size.width * index / 3f
      drawLine(
        color = Color.Black.copy(alpha = 0.35f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = line * 3f,
      )
      drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = line,
      )
      val y = size.height * index / 3f
      drawLine(
        color = Color.Black.copy(alpha = 0.35f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = line * 3f,
      )
      drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = line,
      )
    }
  }
}

private val GridIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "Grid3x3",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 1.6f,
    ) {
      moveTo(3f, 3f)
      lineTo(21f, 3f)
      lineTo(21f, 21f)
      lineTo(3f, 21f)
      close()
      moveTo(9f, 3f)
      lineTo(9f, 21f)
      moveTo(15f, 3f)
      lineTo(15f, 21f)
      moveTo(3f, 9f)
      lineTo(21f, 9f)
      moveTo(3f, 15f)
      lineTo(21f, 15f)
    }
  }.build()
}

private fun orientationLabel(orientation: Orientation): Int = when (orientation) {
  Orientation.PORTRAIT -> R.string.orientation_portrait
  Orientation.LANDSCAPE -> R.string.orientation_landscape
}
