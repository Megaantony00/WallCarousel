// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wallcarousel.app.MainActivity
import com.wallcarousel.app.R
import com.wallcarousel.app.WallCarouselApplication
import kotlinx.coroutines.launch

/**
 * Keeps a dynamic USER_PRESENT receiver alive while Home or Lock advances on
 * unlock. A manifest receiver cannot receive this broadcast on Android 8+.
 *
 * The service only exists while [com.wallcarousel.app.data.model.AppState.hasWakeTarget]
 * is true and stops itself as soon as the option is turned off.
 */
class WakeWatcherService : Service() {
  private val unlockReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != Intent.ACTION_USER_PRESENT) return
      val application = application as? WallCarouselApplication ?: return
      val pending = goAsync()
      application.container.appScope.launch {
        try {
          val state = application.container.repository.ensureLoaded()
          if (state.hasWakeTarget()) application.container.scheduler.enqueueWake()
        } catch (error: Throwable) {
          Log.w(TAG, "Unable to enqueue wake run")
        } finally {
          pending.finish()
        }
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    createChannel()
    ContextCompat.registerReceiver(
      this,
      unlockReceiver,
      IntentFilter(Intent.ACTION_USER_PRESENT),
      // USER_PRESENT is sent by SystemUI, a different uid: NOT_EXPORTED would
      // drop it. The broadcast is protected, so no app can spoof it.
      ContextCompat.RECEIVER_EXPORTED,
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
      0
    }
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)

    val application = application as? WallCarouselApplication
    if (application == null) {
      stopSelf()
      return START_NOT_STICKY
    }
    application.container.appScope.launch {
      if (!application.container.repository.ensureLoaded().hasWakeTarget()) stopSelf()
    }
    return START_STICKY
  }

  override fun onDestroy() {
    runCatching { unregisterReceiver(unlockReceiver) }
    WakeWatcher.onServiceDestroyed()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createChannel() {
    val manager = getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.wake_channel_name),
      NotificationManager.IMPORTANCE_MIN,
    ).apply {
      description = getString(R.string.wake_channel_description)
      setShowBadge(false)
      setSound(null, null)
      enableVibration(false)
    }
    manager.createNotificationChannel(channel)
  }

  private fun buildNotification(): Notification {
    val open = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_wake)
      .setContentTitle(getString(R.string.wake_notification_title))
      .setContentText(getString(R.string.wake_notification_text))
      .setContentIntent(open)
      .setOngoing(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .build()
  }

  private companion object {
    const val TAG = "WakeWatcher"
    const val CHANNEL_ID = "wake_watcher"
    const val NOTIFICATION_ID = 1
  }
}
