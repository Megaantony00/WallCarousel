// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app

import android.app.Application
import android.content.Context
import com.wallcarousel.app.data.ImageStore
import com.wallcarousel.app.data.StateRepository
import com.wallcarousel.app.schedule.WorkScheduler
import com.wallcarousel.app.wallpaper.StaticWallpaperApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WallCarouselApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}

/** Manual dependency container shared by the UI, tiles and workers. */
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val repository = StateRepository(appContext)
  val imageStore = ImageStore(appContext)
  val applier = StaticWallpaperApplier(appContext, repository, imageStore)
  val scheduler = WorkScheduler(appContext)
  val backupManager = BackupManager(appContext, repository, imageStore)

  /** Scope for work that must outlive an activity, such as tile clicks. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    appScope.launch { repository.ensureLoaded() }
  }
}
