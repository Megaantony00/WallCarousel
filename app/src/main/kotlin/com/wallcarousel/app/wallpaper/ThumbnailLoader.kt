// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes and caches downscaled thumbnails for the library grid. */
object ThumbnailLoader {
  private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).toInt(),
  ) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  fun clear() {
    cache.evictAll()
  }

  suspend fun load(file: File, maxSize: Int): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext null
    val key = "${file.absolutePath}|${file.lastModified()}|$maxSize"
    cache.get(key)?.let { return@withContext it }

    val bitmap = runCatching {
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > maxSize) {
          // Round up so the decoded bitmap never exceeds maxSize.
          val sample = (longest + maxSize - 1) / maxSize
          decoder.setTargetSampleSize(maxOf(1, sample))
        }
      }
    }.getOrNull()

    if (bitmap != null) cache.put(key, bitmap)
    bitmap
  }
}
