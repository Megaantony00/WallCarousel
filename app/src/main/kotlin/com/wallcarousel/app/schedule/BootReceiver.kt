// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wallcarousel.app.WallCarouselApplication
import kotlinx.coroutines.launch

/**
 * Restarts the unlock watcher after a reboot or an app update. BOOT_COMPLETED
 * is exempt from the background restrictions and Android 15 allows a
 * specialUse foreground service to be started from it.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
      intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      return
    }
    val application = context.applicationContext as? WallCarouselApplication ?: return
    val pending = goAsync()
    application.container.appScope.launch {
      try {
        val state = application.container.repository.ensureLoaded()
        if (state.hasWakeTarget()) WakeWatcher.start(context)
      } catch (error: Throwable) {
        Log.w(TAG, "Unable to restart the wake watcher")
      } finally {
        pending.finish()
      }
    }
  }

  private companion object {
    const val TAG = "BootReceiver"
  }
}
