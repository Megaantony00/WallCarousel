// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.carousel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.model.Carousel
import com.wallcarousel.app.data.model.Schedule
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.ui.components.DragSelectOverlay
import com.wallcarousel.app.ui.components.SelectionSetSaver
import com.wallcarousel.app.ui.components.formatTimeOfDay
import com.wallcarousel.app.ui.components.rememberThumbnail
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CarouselEditorScreen(
  state: AppState,
  carousel: Carousel,
  viewModel: AppViewModel,
  onBack: () -> Unit,
  onEditWallpaper: (String) -> Unit,
) {
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var showAdd by rememberSaveable { mutableStateOf(false) }
  var selectedIds by rememberSaveable(stateSaver = SelectionSetSaver) {
    mutableStateOf(emptySet())
  }
  val selectionMode = selectedIds.isNotEmpty()
  val carouselIds = carousel.wallpaperIds.filter { state.wallpaper(it) != null }
  val allSelected = carouselIds.isNotEmpty() && selectedIds.containsAll(carouselIds)
  val listState = rememberLazyListState()
  var scheduleTargetName by rememberSaveable { mutableStateOf<String?>(null) }
  val scheduleTarget = scheduleTargetName?.let { name ->
    ApplyTarget.entries.firstOrNull { it.name == name }
  }
  val context = LocalContext.current
  val notificationPermission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { }

  BackHandler {
    if (selectionMode) selectedIds = emptySet() else onBack()
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selectionMode) {
        IconButton(onClick = { selectedIds = emptySet() }) {
          Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }
        Text(
          text = pluralStringResource(
            R.plurals.selected_count,
            selectedIds.size,
            selectedIds.size,
          ),
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        TextButton(
          onClick = {
            selectedIds = if (allSelected) emptySet() else carouselIds.toSet()
          },
        ) {
          Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
        }
        TextButton(
          onClick = {
            viewModel.removeWallpapersFromCarousel(carousel.id, selectedIds)
            selectedIds = emptySet()
          },
        ) {
          Text(stringResource(R.string.remove_selected))
        }
      } else {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
          text = carousel.name,
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = { confirmDelete = true }) {
          Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_carousel))
        }
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val wide = maxWidth >= 840.dp
      if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(24.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              wallpaperItems(
                state,
                carousel,
                viewModel,
                selectedIds = selectedIds,
                selectionMode = selectionMode,
                onAdd = { showAdd = true },
                onEdit = onEditWallpaper,
                onToggle = { id ->
                  selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                onLongPress = { id -> selectedIds = selectedIds + id },
              )
            }
            if (selectionMode) {
              DragSelectOverlay(
                modifier = Modifier.fillMaxSize(),
                keyAt = { position -> listKeyAt(listState, position) },
                onSelect = { id ->
                  if (state.wallpaper(id) != null) selectedIds = selectedIds + id
                },
                onTap = { id ->
                  if (state.wallpaper(id) != null) {
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                  }
                },
                onEdgeScroll = { dy ->
                  listState.dispatchRawDelta(dy)
                },
              )
            }
          }
          Column(
            modifier = Modifier
              .width(400.dp)
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(24.dp),
          ) {
            AssignmentPanel(
              state = state,
              carousel = carousel,
              viewModel = viewModel,
              onSchedule = { scheduleTargetName = it.name },
            )
          }
        }
      } else {
        Box(modifier = Modifier.fillMaxSize()) {
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            item(key = "assignments") {
              AssignmentPanel(
                state = state,
                carousel = carousel,
                viewModel = viewModel,
                onSchedule = { scheduleTargetName = it.name },
              )
            }
            wallpaperItems(
              state,
              carousel,
              viewModel,
              selectedIds = selectedIds,
              selectionMode = selectionMode,
              onAdd = { showAdd = true },
              onEdit = onEditWallpaper,
              onToggle = { id ->
                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
              },
              onLongPress = { id -> selectedIds = selectedIds + id },
            )
          }
          if (selectionMode) {
            DragSelectOverlay(
              modifier = Modifier.fillMaxSize(),
              keyAt = { position -> listKeyAt(listState, position) },
              onSelect = { id ->
                if (state.wallpaper(id) != null) selectedIds = selectedIds + id
              },
              onTap = { id ->
                if (state.wallpaper(id) != null) {
                  selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                }
              },
              onEdgeScroll = { dy ->
                listState.dispatchRawDelta(dy)
              },
            )
          }
        }
      }
    }
  }

  if (scheduleTarget != null) {
    ScheduleDialog(
      target = scheduleTarget,
      initial = state.config(scheduleTarget).schedule,
      onDismiss = { scheduleTargetName = null },
      onSave = { schedule ->
        if (schedule.wake &&
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
        ) {
          notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setSchedule(scheduleTarget, schedule)
        scheduleTargetName = null
      },
    )
  }

  if (showAdd) {
    AddWallpapersDialog(
      state = state,
      carousel = carousel,
      imageFile = viewModel::imageFile,
      onDismiss = { showAdd = false },
      onConfirm = { ids ->
        viewModel.addWallpapersToCarousel(carousel.id, ids)
        showAdd = false
      },
    )
  }

  if (confirmDelete) {
    ConfirmDialog(
      title = stringResource(R.string.delete_carousel),
      body = stringResource(R.string.delete_carousel_body),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        viewModel.deleteCarousel(carousel.id)
        onBack()
      },
      onDismiss = { confirmDelete = false },
    )
  }
}

private fun LazyListScope.wallpaperItems(
  state: AppState,
  carousel: Carousel,
  viewModel: AppViewModel,
  selectedIds: Set<String>,
  selectionMode: Boolean,
  onAdd: () -> Unit,
  onEdit: (String) -> Unit,
  onToggle: (String) -> Unit,
  onLongPress: (String) -> Unit,
) {
  item(key = "wallpapers_header") {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.wallpapers_title),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = pluralStringResource(
            R.plurals.wallpapers_in_carousel,
            state.wallpaperCount(carousel),
            state.wallpaperCount(carousel),
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(onClick = onAdd) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(
          text = stringResource(R.string.add),
          modifier = Modifier.padding(start = 8.dp),
        )
      }
    }
  }

  if (carousel.wallpaperIds.isEmpty()) {
    item(key = "wallpapers_empty") {
      Text(
        text = stringResource(R.string.carousel_empty_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
      )
    }
  }

  items(items = carousel.wallpaperIds, key = { it }) { wallpaperId ->
    val wallpaper = state.wallpaper(wallpaperId)
    if (wallpaper != null) {
      val index = carousel.wallpaperIds.indexOf(wallpaperId)
      WallpaperRow(
        wallpaperName = wallpaper.displayName(),
        imageFile = wallpaper.previewVariant()?.let { viewModel.imageFile(it.fileName) },
        position = index + 1,
        selected = wallpaperId in selectedIds,
        selectionMode = selectionMode,
        canMoveUp = index > 0,
        canMoveDown = index < carousel.wallpaperIds.lastIndex,
        onClick = {
          if (selectionMode) onToggle(wallpaperId) else onEdit(wallpaperId)
        },
        onLongClick = { onLongPress(wallpaperId) },
        onMoveUp = { viewModel.moveWallpaper(carousel.id, wallpaperId, up = true) },
        onMoveDown = { viewModel.moveWallpaper(carousel.id, wallpaperId, up = false) },
        onEdit = { onEdit(wallpaperId) },
        onRemove = { viewModel.removeWallpaperFromCarousel(carousel.id, wallpaperId) },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperRow(
  wallpaperName: String,
  imageFile: File?,
  position: Int,
  selected: Boolean,
  selectionMode: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onEdit: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    colors = if (selected) {
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
      CardDefaults.cardColors()
    },
  ) {
    Row(
      modifier = Modifier.padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        val bitmap = rememberThumbnail(imageFile, 180)
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
        if (selected) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
          )
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(28.dp),
          )
        }
      }
      Text(
        text = "$position.",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = wallpaperName,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (!selectionMode) {
        IconButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_wallpaper))
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
          Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
          Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
        }
        IconButton(onClick = onRemove) {
          Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
        }
      }
    }
  }
}

@Composable
private fun AssignmentPanel(
  state: AppState,
  carousel: Carousel,
  viewModel: AppViewModel,
  onSchedule: (ApplyTarget) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(R.string.assignment_title),
      style = MaterialTheme.typography.titleMedium,
    )
    TargetAssignmentCard(
      target = ApplyTarget.HOME,
      state = state,
      carousel = carousel,
      onAssign = { viewModel.assignCarousel(ApplyTarget.HOME, carousel.id) },
      onUnassign = { viewModel.assignCarousel(ApplyTarget.HOME, null) },
      onApply = { viewModel.applyNow(ApplyTarget.HOME) },
      onSchedule = { onSchedule(ApplyTarget.HOME) },
    )
    TargetAssignmentCard(
      target = ApplyTarget.LOCK,
      state = state,
      carousel = carousel,
      onAssign = { viewModel.assignCarousel(ApplyTarget.LOCK, carousel.id) },
      onUnassign = { viewModel.assignCarousel(ApplyTarget.LOCK, null) },
      onApply = { viewModel.applyNow(ApplyTarget.LOCK) },
      onSchedule = { onSchedule(ApplyTarget.LOCK) },
    )
    if (state.wallpaperCount(carousel) == 0) {
      Text(
        text = stringResource(R.string.carousel_empty_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun TargetAssignmentCard(
  target: ApplyTarget,
  state: AppState,
  carousel: Carousel,
  onAssign: () -> Unit,
  onUnassign: () -> Unit,
  onApply: () -> Unit,
  onSchedule: () -> Unit,
) {
  val config = state.config(target)
  val assigned = config.carouselId == carousel.id
  val hasWallpapers = state.wallpaperCount(carousel) > 0

  Card {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = if (target == ApplyTarget.HOME) Icons.Default.Home else Icons.Default.Lock,
          contentDescription = null,
        )
        Text(
          text = stringResource(targetLabel(target)),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (assigned) {
          Text(
            text = stringResource(R.string.in_use),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      if (assigned) {
        Text(
          text = scheduleSummary(config.schedule),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledTonalButton(onClick = onApply, enabled = hasWallpapers) {
            Text(stringResource(R.string.apply_now))
          }
          OutlinedButton(onClick = onSchedule) {
            Text(stringResource(R.string.schedule))
          }
          Spacer(Modifier.weight(1f))
          IconButton(onClick = onUnassign) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
          }
        }
      } else {
        Button(onClick = onAssign, enabled = hasWallpapers) {
          Text(
            stringResource(
              if (target == ApplyTarget.HOME) R.string.use_for_home else R.string.use_for_lock,
            ),
          )
        }
      }
    }
  }
}

private fun listKeyAt(listState: LazyListState, position: Offset): String? =
  listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
    position.y >= item.offset.toFloat() &&
      position.y <= (item.offset + item.size).toFloat()
  }?.key as? String

@Composable
private fun intervalSummary(minutes: Int): String {
  val hours = minutes / 60
  val remainingMinutes = minutes % 60
  return when {
    hours == 0 -> pluralStringResource(
      R.plurals.schedule_interval_minutes_summary,
      remainingMinutes,
      remainingMinutes,
    )
    remainingMinutes == 0 -> pluralStringResource(
      R.plurals.schedule_interval_hours_summary,
      hours,
      hours,
    )
    else -> {
      val hoursText = pluralStringResource(
        R.plurals.schedule_interval_hours_summary,
        hours,
        hours,
      )
      val minutesText = pluralStringResource(
        R.plurals.schedule_interval_minutes_part,
        remainingMinutes,
        remainingMinutes,
      )
      stringResource(
        R.string.schedule_interval_hours_minutes_summary,
        hoursText,
        minutesText,
      )
    }
  }
}

@Composable
private fun scheduleSummary(schedule: Schedule): String {
  val intervalMinutes = schedule.intervalMinutes
  val intervalText = if (intervalMinutes != null) {
    intervalSummary(intervalMinutes)
  } else {
    null
  }
  val dailyText = if (schedule.dailyMinutes.isNotEmpty()) {
    stringResource(
      R.string.schedule_daily_summary,
      schedule.dailyMinutes.sorted().joinToString(", ") { formatTimeOfDay(it) },
    )
  } else {
    null
  }
  val wakeText = if (schedule.wake) stringResource(R.string.schedule_wake_summary) else null
  val parts = listOfNotNull(intervalText, dailyText, wakeText)
  return if (parts.isEmpty()) stringResource(R.string.schedule_manual) else parts.joinToString(" · ")
}

@Composable
private fun targetLabel(target: ApplyTarget): Int = when (target) {
  ApplyTarget.HOME -> R.string.home_screen
  ApplyTarget.LOCK -> R.string.lock_screen
  ApplyTarget.BOTH -> R.string.home_screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
  target: ApplyTarget,
  initial: Schedule,
  onDismiss: () -> Unit,
  onSave: (Schedule) -> Unit,
) {
  var intervalEnabled by rememberSaveable { mutableStateOf(initial.intervalMinutes != null) }
  var interval by rememberSaveable {
    mutableFloatStateOf((initial.intervalMinutes ?: 120).toFloat())
  }
  var dailyEnabled by rememberSaveable { mutableStateOf(initial.dailyMinutes.isNotEmpty()) }
  var times by rememberSaveable { mutableStateOf(initial.dailyMinutes) }
  var wake by rememberSaveable { mutableStateOf(initial.wake) }
  var showTimePicker by rememberSaveable { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.schedule_title, stringResource(targetLabel(target)))) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        ScheduleToggle(
          label = stringResource(R.string.schedule_interval),
          checked = intervalEnabled,
          onCheckedChange = { intervalEnabled = it },
        )
        if (intervalEnabled) {
          Text(
            text = intervalSummary(interval.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
          )
          Slider(
            value = interval,
            onValueChange = { interval = it },
            valueRange = 15f..720f,
            steps = 46,
            modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
        ScheduleToggle(
          label = stringResource(R.string.schedule_daily),
          checked = dailyEnabled,
          onCheckedChange = { dailyEnabled = it },
        )
        if (dailyEnabled) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(start = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            times.sorted().forEach { time ->
              InputChip(
                selected = false,
                onClick = { times = times - time },
                label = { Text(formatTimeOfDay(time)) },
                trailingIcon = {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove),
                  )
                },
              )
            }
            OutlinedButton(onClick = { showTimePicker = true }) {
              Icon(Icons.Default.Add, contentDescription = null)
              Text(
                text = stringResource(R.string.add_time),
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          }
          if (times.isEmpty()) {
            Text(
              text = stringResource(R.string.no_times),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 16.dp),
            )
          }
        }
        ScheduleToggle(
          label = stringResource(R.string.schedule_wake),
          checked = wake,
          onCheckedChange = { wake = it },
        )
        if (wake) {
          Text(
            text = stringResource(R.string.schedule_wake_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(
            Schedule(
              intervalMinutes = if (intervalEnabled) interval.roundToInt() else null,
              dailyMinutes = if (dailyEnabled) times.sorted() else emptyList(),
              wake = wake,
            ),
          )
        },
        enabled = !dailyEnabled || times.isNotEmpty(),
      ) {
        Text(stringResource(R.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )

  if (showTimePicker) {
    TimePickerDialog(
      initialMinutes = times.firstOrNull() ?: (8 * 60),
      onDismiss = { showTimePicker = false },
      onConfirm = { minutes ->
        times = (times + minutes).distinct().sorted()
        showTimePicker = false
      },
    )
  }
}

@Composable
private fun ScheduleToggle(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f).padding(end = 12.dp),
    )
    Switch(checked = checked, onCheckedChange = null)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
  initialMinutes: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  val context = LocalContext.current
  val state = rememberTimePickerState(
    initialHour = initialMinutes / 60,
    initialMinute = initialMinutes % 60,
    is24Hour = DateFormat.is24HourFormat(context),
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
        Text(stringResource(R.string.add))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
    text = { TimePicker(state = state) },
  )
}

@Composable
private fun AddWallpapersDialog(
  state: AppState,
  carousel: Carousel,
  imageFile: (String) -> File,
  onDismiss: () -> Unit,
  onConfirm: (List<String>) -> Unit,
) {
  val available = state.wallpapers.filterNot { it.id in carousel.wallpaperIds }
  var selected by remember { mutableStateOf(emptySet<String>()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.choose_wallpapers)) },
    text = {
      if (available.isEmpty()) {
        Text(stringResource(R.string.all_wallpapers_added))
      } else {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
          items(items = available, key = { it.id }) { wallpaper ->
            val checked = wallpaper.id in selected
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable {
                  selected = if (checked) selected - wallpaper.id else selected + wallpaper.id
                }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Checkbox(checked = checked, onCheckedChange = null)
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant),
              ) {
                val variant = wallpaper.previewVariant()
                val bitmap = rememberThumbnail(variant?.let { imageFile(it.fileName) }, 150)
                if (bitmap != null) {
                  Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
              }
              Text(
                text = wallpaper.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(selected.toList()) },
        enabled = selected.isNotEmpty(),
      ) {
        Text(stringResource(R.string.add))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}
