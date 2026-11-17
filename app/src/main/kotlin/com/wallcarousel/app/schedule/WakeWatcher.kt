// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.schedule

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops the unlock watcher without keeping it alive when unused. */
object WakeWatcher {
  // The service is started only once per process; repeated calls would just
  // refresh its notification on every state save.
  @Volatile
  private var running = false

  fun start(context: Context) {
    if (running) return
    val intent = Intent(context, WakeWatcherService::class.java)
    try {
      ContextCompat.startForegroundService(context, intent)
      running = true
    } catch (error: RuntimeException) {
      // Android can refuse a background start; BOOT_COMPLETED or the next app
      // launch starts the watcher again.
    }
  }

  fun stop(context: Context) {
    running = false
    context.stopService(Intent(context, WakeWatcherService::class.java))
  }

  /** Called by the service when it is destroyed, so a later start works again. */
  internal fun onServiceDestroyed() {
    running = false
  }
}
