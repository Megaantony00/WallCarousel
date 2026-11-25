// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.wallpaper

import android.graphics.Bitmap
import android.util.LruCache
import com.wallcarousel.app.data.model.Variant
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders small previews with the full crop/band recipe, so library cards show
 * the wallpaper as it will actually look. Results are cached by file, recipe
 * and size; the grid never renders on the main thread.
 */
object WallpaperPreviewLoader {
  private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 16).toInt(),
  ) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  fun clear() {
    cache.evictAll()
  }

  suspend fun load(
    file: File,
    variant: Variant,
    width: Int,
    height: Int,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists() || width <= 0 || height <= 0) return@withContext null
    val key = "${file.absolutePath}|${file.lastModified()}|$variant|${width}x${height}"
    cache.get(key)?.let { return@withContext it }

    val bitmap = WallpaperRenderer.render(
      file = file,
      variant = variant,
      targetWidth = width,
      targetHeight = height,
    )
    if (bitmap != null) cache.put(key, bitmap)
    bitmap
  }
}
