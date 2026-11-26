// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.wallcarousel.app.data.ImageStore
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.StateRepository
import com.wallcarousel.app.schedule.WorkScheduler
import com.wallcarousel.app.update.UpdateManager
import com.wallcarousel.app.wallpaper.StaticWallpaperApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WallCarouselApplication : Application() {
  lateinit var container: AppContainer
    private set
  private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

  // Writing FLAG_LOCK while the device is unlocked makes Samsung briefly show
  // the keyguard. When unlocked we only refresh Home on rotation and finish the
  // Lock refresh when the screen turns off, when the keyguard is not visible.
  private val screenOffReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != Intent.ACTION_SCREEN_OFF) return
      val pending = goAsync()
      container.appScope.launch {
        try {
          container.applier.reapplyCurrent(ApplyTarget.LOCK)
        } finally {
          pending.finish()
        }
      }
    }
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(AppLanguage.wrap(base))
  }

  override fun onCreate() {
    super.onCreate()
    lastOrientation = resources.configuration.orientation
    container = AppContainer(this)
    ContextCompat.registerReceiver(
      this,
      screenOffReceiver,
      IntentFilter(Intent.ACTION_SCREEN_OFF),
      // SCREEN_OFF is sent by the system, a different uid: NOT_EXPORTED would
      // drop it. The broadcast is protected, so no app can spoof it.
      ContextCompat.RECEIVER_EXPORTED,
    )
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (lastOrientation != newConfig.orientation) {
      lastOrientation = newConfig.orientation
      val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
      container.appScope.launch {
        if (locked) {
          container.applier.reapplyCurrent(ApplyTarget.LOCK)
          container.applier.reapplyCurrent(ApplyTarget.HOME)
        } else {
          // Do not touch the lock wallpaper while the device is unlocked.
          container.applier.reapplyCurrent(ApplyTarget.HOME)
        }
      }
    }
  }
}

/** Manual dependency container shared by the UI, tiles and workers. */
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val repository = StateRepository(appContext)
  val imageStore = ImageStore(appContext)

  /** Scope for work that must outlive an activity, such as tile clicks. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val applier = StaticWallpaperApplier(appContext, repository, imageStore, appScope)
  val scheduler = WorkScheduler(appContext)
  val backupManager = BackupManager(appContext, repository, imageStore)
  val updater = UpdateManager(appContext)

  init {
    updater.cleanup()
    appScope.launch {
      repository.ensureLoaded()
      val locked = appContext.getSystemService(KeyguardManager::class.java)
        ?.isKeyguardLocked == true
      if (locked) {
        applier.reapplyCurrent(ApplyTarget.LOCK)
        applier.reapplyCurrent(ApplyTarget.HOME)
      } else {
        applier.reapplyCurrent(ApplyTarget.HOME)
      }
      applier.warmOrientationCache(ApplyTarget.BOTH)
    }
  }
}
