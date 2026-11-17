// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app

import android.content.Context
import android.net.Uri
import com.wallcarousel.app.data.ImageStore
import com.wallcarousel.app.data.StateRepository
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Exports the complete local dataset (metadata plus original images) as a zip
 * written to a user-selected document. No network access is involved.
 */
class BackupManager(
  context: Context,
  private val repository: StateRepository,
  private val imageStore: ImageStore,
) {
  private val appContext = context.applicationContext
  private val json = Json {
    encodeDefaults = true
    prettyPrint = true
  }

  suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      val state = repository.ensureLoaded()
      val stream = appContext.contentResolver.openOutputStream(uri) ?: return@runCatching false
      ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
        zip.putNextEntry(ZipEntry("wallcarousel.json"))
        zip.write(json.encodeToString(state).toByteArray())
        zip.closeEntry()

        state.wallpapers.forEach { wallpaper ->
          listOfNotNull(wallpaper.portrait, wallpaper.landscape).forEach { variant ->
            val file = imageStore.file(variant.fileName)
            if (file.exists()) {
              zip.putNextEntry(ZipEntry("wallpapers/${variant.fileName}"))
              file.inputStream().use { input -> input.copyTo(zip) }
              zip.closeEntry()
            }
          }
        }
      }
      true
    }.getOrDefault(false)
  }
}
