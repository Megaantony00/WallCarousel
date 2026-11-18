// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.ui.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens the local gallery picker instead of the Photo Picker. On Samsung
 * devices it targets the system Gallery directly, so the real file name is
 * usually available. Only the chosen images are granted to the app.
 */
class PickGalleryImages : ActivityResultContract<Unit, List<Uri>>() {
  override fun createIntent(context: Context, input: Unit): Intent =
    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
      type = "image/*"
      putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      val samsungGallery = runCatching {
        context.packageManager.getPackageInfo(SAMSUNG_GALLERY, 0)
      }.isSuccess
      if (samsungGallery) setPackage(SAMSUNG_GALLERY)
    }

  override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
    if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
    val clip = intent.clipData
    if (clip != null && clip.itemCount > 0) {
      return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }
    return listOfNotNull(intent.data)
  }

  private companion object {
    const val SAMSUNG_GALLERY = "com.sec.android.gallery3d"
  }
}
