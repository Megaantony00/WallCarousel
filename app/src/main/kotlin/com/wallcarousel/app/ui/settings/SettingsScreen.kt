// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallcarousel.app.AppLanguage
import com.wallcarousel.app.R
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.model.ThemeMode
import com.wallcarousel.app.ui.AppViewModel
import com.wallcarousel.app.ui.components.ConfirmDialog
import com.wallcarousel.app.ui.components.formatBytes
import com.wallcarousel.app.update.UpdateState
import java.time.LocalDate
import java.util.Locale

@Composable
fun SettingsScreen(state: AppState, viewModel: AppViewModel) {
  val context = LocalContext.current
  val updateState by viewModel.updateState.collectAsStateWithLifecycle()
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var storageBytes by remember { mutableLongStateOf(0L) }
  val languageTags = remember { context.resources.getStringArray(R.array.app_languages) }
  val systemLanguageLabel = stringResource(R.string.language_system)
  val languageOptions = remember(languageTags, systemLanguageLabel) {
    listOf(LanguageOption(null, systemLanguageLabel)) +
      languageTags.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        val label = locale.getDisplayName(locale).replaceFirstChar { first ->
          if (first.isLowerCase()) first.titlecase(locale) else first.toString()
        }
        LanguageOption(tag, label)
      }
  }
  var selectedLanguage by remember {
    mutableStateOf(AppLanguage.selectedTag(context)?.takeIf { it in languageTags })
  }
  var languageMenuOpen by remember { mutableStateOf(false) }
  val currentLanguageLabel = languageOptions.firstOrNull { it.tag == selectedLanguage }?.label
    ?: systemLanguageLabel

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

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.wallpaper_palette),
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = stringResource(R.string.wallpaper_palette_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = state.wallpaperPalette,
              onCheckedChange = viewModel::setWallpaperPalette,
            )
          }

          if (state.wallpaperPalette) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = state.wallpaperPaletteTarget == ApplyTarget.HOME,
                onClick = { viewModel.setWallpaperPaletteTarget(ApplyTarget.HOME) },
                label = { Text(stringResource(R.string.palette_target_home)) },
                modifier = Modifier.heightIn(min = 48.dp),
              )
              FilterChip(
                selected = state.wallpaperPaletteTarget == ApplyTarget.LOCK,
                onClick = { viewModel.setWallpaperPaletteTarget(ApplyTarget.LOCK) },
                label = { Text(stringResource(R.string.palette_target_lock)) },
                modifier = Modifier.heightIn(min = 48.dp),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.pre_render_next),
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = stringResource(R.string.pre_render_next_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = state.preRenderNext,
              onCheckedChange = viewModel::setPreRenderNext,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.fast_wallpaper_write),
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = stringResource(R.string.fast_wallpaper_write_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = state.jpegWallpaperWrite,
              onCheckedChange = viewModel::setJpegWallpaperWrite,
            )
          }
        }
      }

      item {
        SettingsSection(title = stringResource(R.string.language)) {
          Text(
            text = stringResource(R.string.language_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = { languageMenuOpen = true },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = currentLanguageLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
              )
              Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
              expanded = languageMenuOpen,
              onDismissRequest = { languageMenuOpen = false },
            ) {
              languageOptions.forEach { option ->
                DropdownMenuItem(
                  text = { Text(option.label) },
                  onClick = {
                    languageMenuOpen = false
                    if (selectedLanguage != option.tag) {
                      AppLanguage.setSelectedTag(context, option.tag)
                      selectedLanguage = option.tag
                      context.findActivity()?.recreate()
                    }
                  },
                  trailingIcon = {
                    if (selectedLanguage == option.tag) {
                      Icon(Icons.Default.Check, contentDescription = null)
                    }
                  },
                )
              }
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
        SettingsSection(title = stringResource(R.string.updates)) {
          Text(
            text = stringResource(R.string.update_current_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.auto_update_check),
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = stringResource(R.string.auto_update_check_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = state.autoUpdateCheck,
              onCheckedChange = viewModel::setAutoUpdateCheck,
            )
          }
          OutlinedButton(
            onClick = viewModel::checkForUpdates,
            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
          ) {
            Text(stringResource(R.string.check_updates))
          }

          when (val update = updateState) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Text(
              text = stringResource(R.string.checking_updates),
              style = MaterialTheme.typography.bodySmall,
            )
            UpdateState.UpToDate -> Text(
              text = stringResource(R.string.up_to_date),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is UpdateState.Available -> {
              Text(
                text = stringResource(R.string.update_available, update.release.versionName),
                style = MaterialTheme.typography.titleSmall,
              )
              if (update.release.notes.isNotBlank()) {
                Text(
                  text = update.release.notes,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 6,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Button(onClick = viewModel::downloadAndInstallUpdate) {
                Text(stringResource(R.string.download_and_install))
              }
            }
            is UpdateState.Downloading -> {
              LinearProgressIndicator(
                progress = { update.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
              )
              Text(
                text = stringResource(R.string.downloading_update, update.progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            UpdateState.ReadyToInstall -> {
              Text(
                text = stringResource(R.string.update_ready),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(onClick = viewModel::installUpdate) {
                Text(stringResource(R.string.install_now))
              }
            }
            UpdateState.NeedsInstallPermission -> {
              Text(
                text = stringResource(R.string.install_permission_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(onClick = viewModel::openInstallPermissionSettings) {
                Text(stringResource(R.string.open_settings))
              }
              OutlinedButton(onClick = viewModel::installUpdate) {
                Text(stringResource(R.string.install_now))
              }
            }
            is UpdateState.Failed -> {
              val message = when (update.reason) {
                UpdateState.Reason.CHECK -> R.string.update_check_failed
                UpdateState.Reason.DOWNLOAD -> R.string.update_download_failed
                UpdateState.Reason.VERIFY -> R.string.update_verify_failed
                UpdateState.Reason.INSTALL -> R.string.update_install_failed
              }
              Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
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

private data class LanguageOption(val tag: String?, val label: String)

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
