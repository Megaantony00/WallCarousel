// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedImage(
  val fileName: String,
  val width: Int,
  val height: Int,
  val displayName: String,
)

/** Owns the original image files inside the app sandbox. */
class ImageStore(context: Context) {
  private val appContext = context.applicationContext

  val imagesDir: File
    get() = File(appContext.filesDir, "wallpapers").apply { mkdirs() }

  fun file(fileName: String): File = File(imagesDir, fileName)

  /**
   * Copies [uri] into the sandbox and reads its oriented dimensions. Returns
   * null when the content cannot be decoded as an image. When the provider does
   * not expose a usable display name, the import time is used instead.
   */
  suspend fun import(uri: Uri): ImportedImage? = withContext(Dispatchers.IO) {
    val resolver = appContext.contentResolver
    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    val extension = MimeTypeMap.getSingleton()
      .getExtensionFromMimeType(mimeType)
      ?.lowercase()
      ?: "jpg"
    val fileName = "${UUID.randomUUID()}.$extension"
    val target = file(fileName)

    val copied = runCatching {
      resolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
        true
      } ?: false
    }.getOrDefault(false)

    if (!copied) {
      target.delete()
      return@withContext null
    }

    val size = runCatching { readSize(target) }.getOrNull()
    if (size == null || size.first <= 0 || size.second <= 0) {
      target.delete()
      return@withContext null
    }

    ImportedImage(
      fileName = fileName,
      width = size.first,
      height = size.second,
      displayName = displayName(uri),
    )
  }

  fun delete(fileName: String) {
    file(fileName).delete()
  }

  fun deleteAll() {
    imagesDir.listFiles()?.forEach { it.delete() }
  }

  fun totalBytes(): Long = imagesDir.listFiles()?.sumOf { it.length() } ?: 0L

  private fun readSize(file: File): Pair<Int, Int> {
    var width = 0
    var height = 0
    val bitmap: Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
      width = info.size.width
      height = info.size.height
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      decoder.setTargetSize(1, 1)
    }
    bitmap.recycle()
    return width to height
  }

  private fun displayName(uri: Uri): String {
    val fromProvider = runCatching {
      appContext.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      }
    }.getOrNull()
      ?.substringBeforeLast('.')
      ?.trim()

    // Some Photo Picker builds expose only a numeric MediaStore id; it is not
    // meaningful to the user, so use the import time as a readable fallback.
    val usable = fromProvider?.takeIf {
      it.isNotBlank() && it != uri.lastPathSegment && !it.all(Char::isDigit)
    }
    return usable ?: SimpleDateFormat("d MMM yyyy HH.mm", Locale.getDefault()).format(Date())
  }
}
