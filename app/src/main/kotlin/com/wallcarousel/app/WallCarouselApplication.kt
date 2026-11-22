// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
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

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(AppLanguage.wrap(base))
  }

  override fun onCreate() {
    super.onCreate()
    lastOrientation = resources.configuration.orientation
    container = AppContainer(this)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (lastOrientation != newConfig.orientation) {
      lastOrientation = newConfig.orientation
      val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
      val first = if (locked) ApplyTarget.LOCK else ApplyTarget.HOME
      val second = if (locked) ApplyTarget.HOME else ApplyTarget.LOCK
      container.appScope.launch {
        container.applier.reapplyCurrent(first)
        container.applier.reapplyCurrent(second)
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
      applier.reapplyCurrent(ApplyTarget.BOTH)
      applier.warmOrientationCache(ApplyTarget.BOTH)
    }
  }
}
