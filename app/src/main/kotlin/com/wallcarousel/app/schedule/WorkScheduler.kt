// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.schedule

import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wallcarousel.app.data.model.AppState
import com.wallcarousel.app.data.model.ApplyTarget
import com.wallcarousel.app.data.model.Schedule
import com.wallcarousel.app.data.model.SchedulePlanner
import com.wallcarousel.app.data.model.TargetConfig
import java.util.concurrent.TimeUnit

/**
 * Translates the persisted triggers into WorkManager work. WorkManager is the
 * only background scheduler used: the interval is periodic work, daily times
 * use one time work that is re-enqueued after every run and unlock runs are
 * enqueued by the watcher. The triggers are independent, so a target can have
 * an interval, daily times, the unlock trigger or any combination of them.
 */
class WorkScheduler(context: Context) {
  private val appContext = context.applicationContext

  fun sync(state: AppState) {
    syncTarget(ApplyTarget.HOME, state.home, state)
    syncTarget(ApplyTarget.LOCK, state.lock, state)

    // The watcher only exists while at least one target advances on unlock.
    if (state.hasWakeTarget()) {
      WakeWatcher.start(appContext)
    } else {
      WakeWatcher.stop(appContext)
      WorkManager.getInstance(appContext).cancelUniqueWork(WAKE_WORK_NAME)
    }
  }

  /** Re-enqueues the next daily run after one has just completed. */
  fun scheduleNext(target: ApplyTarget, state: AppState) {
    val config = state.config(target)
    if (config.schedule.dailyMinutes.isNotEmpty()) {
      enqueueDaily(target, config, state, ExistingWorkPolicy.REPLACE)
    }
  }

  /**
   * Enqueues one run that advances every target with the unlock trigger on.
   * The watcher calls this on unlock; KEEP avoids stacking runs when two
   * unlocks arrive close together.
   */
  fun enqueueWake() {
    val builder = OneTimeWorkRequestBuilder<ApplyWallpaperWorker>()
      .setInputData(
        workDataOf(ApplyWallpaperWorker.KEY_TRIGGER to ApplyWallpaperWorker.TRIGGER_WAKE),
      )
      .addTag(TAG)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    }
    val request = builder.build()
    WorkManager.getInstance(appContext)
      .enqueueUniqueWork(WAKE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
  }

  fun cancelAll() {
    WorkManager.getInstance(appContext).apply {
      for (target in listOf(ApplyTarget.HOME, ApplyTarget.LOCK)) {
        cancelUniqueWork(intervalName(target))
        cancelUniqueWork(dailyName(target))
        cancelUniqueWork(legacyName(target))
      }
      cancelUniqueWork(WAKE_WORK_NAME)
    }
    WakeWatcher.stop(appContext)
  }

  private fun syncTarget(target: ApplyTarget, config: TargetConfig, state: AppState) {
    val manager = WorkManager.getInstance(appContext)
    // Work enqueued by the previous single-schedule version.
    manager.cancelUniqueWork(legacyName(target))
    if (state.carousel(config.carouselId) == null) {
      manager.cancelUniqueWork(intervalName(target))
      manager.cancelUniqueWork(dailyName(target))
      return
    }

    syncInterval(target, config.schedule)
    if (config.schedule.dailyMinutes.isNotEmpty()) {
      enqueueDaily(target, config, state, ExistingWorkPolicy.REPLACE)
    } else {
      manager.cancelUniqueWork(dailyName(target))
    }
  }

  private fun syncInterval(target: ApplyTarget, schedule: Schedule) {
    val manager = WorkManager.getInstance(appContext)
    val minutes = schedule.intervalMinutes
    if (minutes == null) {
      manager.cancelUniqueWork(intervalName(target))
      return
    }
    val period = minutes.coerceAtLeast(SchedulePlanner.MIN_INTERVAL_MINUTES).toLong()
    val request = PeriodicWorkRequestBuilder<ApplyWallpaperWorker>(period, TimeUnit.MINUTES)
      .setInputData(workDataOf(ApplyWallpaperWorker.KEY_TARGET to target.name))
      .addTag(TAG)
      .build()
    manager.enqueueUniquePeriodicWork(
      intervalName(target),
      ExistingPeriodicWorkPolicy.REPLACE,
      request,
    )
  }

  private fun enqueueDaily(
    target: ApplyTarget,
    config: TargetConfig,
    state: AppState,
    policy: ExistingWorkPolicy,
  ) {
    if (state.carousel(config.carouselId) == null) return
    val manager = WorkManager.getInstance(appContext)
    val delay = SchedulePlanner.nextDelayMillis(config.schedule, System.currentTimeMillis())
    if (delay == null) {
      manager.cancelUniqueWork(dailyName(target))
      return
    }
    val request = OneTimeWorkRequestBuilder<ApplyWallpaperWorker>()
      .setInitialDelay(delay, TimeUnit.MILLISECONDS)
      .setInputData(workDataOf(ApplyWallpaperWorker.KEY_TARGET to target.name))
      .addTag(TAG)
      .build()
    manager.enqueueUniqueWork(dailyName(target), policy, request)
  }

  private fun intervalName(target: ApplyTarget): String =
    "wallcarousel_${target.name.lowercase()}_interval"

  private fun dailyName(target: ApplyTarget): String =
    "wallcarousel_${target.name.lowercase()}_daily"

  /** Name used before the triggers became independent. */
  private fun legacyName(target: ApplyTarget): String =
    "wallcarousel_${target.name.lowercase()}"

  companion object {
    const val TAG = "wallcarousel_schedule"
    private const val WAKE_WORK_NAME = "wallcarousel_wake"
  }
}
