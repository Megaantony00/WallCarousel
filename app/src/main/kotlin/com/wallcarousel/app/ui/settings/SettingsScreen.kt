// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.ThemeMode
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.ui.components.formatBytes
import java.time.LocalDate

@Composable
fun SettingsScreen(state: AppState, viewModel: AppViewModel) {
  val context = LocalContext.current
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var storageBytes by remember { mutableLongStateOf(0L) }

  LaunchedEffect(state.wallpapers) {
    storageBytes = viewModel.storageBytes()
  }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri ->
    if (uri != null) viewModel.exportData(uri)
  }

  val version = remember {
    runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val horizontalPadding = ((maxWidth - 840.dp) / 2).coerceAtLeast(24.dp)

    LazyColumn(
      contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        Text(
          text = stringResource(R.string.settings_title),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(horizontal = 8.dp),
        )
      }

      item {
        SettingsSection(title = stringResource(R.string.appearance)) {
          Text(
            text = stringResource(R.string.theme),
            style = MaterialTheme.typography.bodyMedium,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = state.themeMode == mode,
                onClick = { viewModel.setThemeMode(mode) },
                label = { Text(stringResource(themeLabel(mode))) },
                modifier = Modifier.heightIn(min = 48.dp),
              )
            }
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.dynamic_color),
                  style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                  text = stringResource(R.string.dynamic_color_summary),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
              )
            }
          }
        }
      }

      item {
        SettingsSection(title = stringResource(R.string.data)) {
          Text(
            text = pluralStringResource(
              R.plurals.storage_summary,
              state.wallpapers.size,
              state.wallpapers.size,
              formatBytes(storageBytes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = stringResource(R.string.export_data_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = {
              exportLauncher.launch("wallcarousel-${LocalDate.now()}.zip")
            },
          ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Text(
              text = stringResource(R.string.export_data),
              modifier = Modifier.padding(start = 8.dp),
            )
          }

          Text(
            text = stringResource(R.string.delete_all_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(onClick = { confirmDelete = true }) {
            Text(
              text = stringResource(R.string.delete_all),
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      item {
        SettingsSection(title = stringResource(R.string.about)) {
          Text(
            text = stringResource(R.string.version_label, version),
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = stringResource(R.string.license_label),
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = stringResource(R.string.license_value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = stringResource(R.string.privacy_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
          )
          Text(
            text = stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = stringResource(R.string.no_network_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (confirmDelete) {
    ConfirmDialog(
      title = stringResource(R.string.delete_all),
      body = stringResource(R.string.delete_all_body),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = { viewModel.deleteAllData() },
      onDismiss = { confirmDelete = false },
    )
  }
}

@Composable
private fun SettingsSection(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      content()
    }
  }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
  ThemeMode.SYSTEM -> R.string.theme_system
  ThemeMode.LIGHT -> R.string.theme_light
  ThemeMode.DARK -> R.string.theme_dark
}
