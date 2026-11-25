// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.Variant
import com.wallcarousel.app.wallpaper.ThumbnailLoader
import com.wallcarousel.app.wallpaper.WallpaperPreviewLoader
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.time.LocalTime

/** Loads a cached thumbnail for [file], or null while it is being decoded. */
@Composable
fun rememberThumbnail(file: File?, maxSize: Int): Bitmap? {
  val state = produceState<Bitmap?>(
    initialValue = null,
    file?.absolutePath,
    file?.lastModified(),
    maxSize,
  ) {
    value = file?.let { ThumbnailLoader.load(it, maxSize) }
  }
  return state.value
}

/** Loads a cached preview rendered with the full crop, filter and band recipe. */
@Composable
fun rememberWallpaperPreview(
  file: File?,
  variant: Variant?,
  width: Int,
  height: Int,
): Bitmap? {
  val state = produceState<Bitmap?>(
    initialValue = null,
    file?.absolutePath,
    file?.lastModified(),
    variant,
    width,
    height,
  ) {
    value = if (file != null && variant != null) {
      WallpaperPreviewLoader.load(file, variant, width, height)
    } else {
      null
    }
  }
  return state.value
}

@Composable
fun ScreenHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.headlineMedium)
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    actions()
  }
}

@Composable
fun EmptyState(
  title: String,
  body: String,
  actionLabel: String,
  onAction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Button(onClick = onAction) { Text(actionLabel) }
  }
}

@Composable
fun ConfirmDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      TextButton(
        onClick = {
          onDismiss()
          onConfirm()
        },
      ) {
        Text(confirmLabel, color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}

fun formatBytes(bytes: Long): String {
  val megabytes = bytes / (1024.0 * 1024.0)
  return if (megabytes < 0.1 && bytes > 0) "< 0.1 MB" else String.format(Locale.getDefault(), "%.1f MB", megabytes)
}

fun formatTimeOfDay(minutesOfDay: Int): String {
  val time = LocalTime.of((minutesOfDay / 60) % 24, minutesOfDay % 60)
  return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}
