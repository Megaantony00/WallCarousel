// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wallcarousel.app.WallCarouselApplication
import com.wallcarousel.app.data.model.ApplyTarget
import kotlin.coroutines.cancellation.CancellationException

/** Applies the next wallpaper for the target encoded in the request. */
class ApplyWallpaperWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val application = applicationContext as? WallCarouselApplication
      ?: return Result.failure()

    if (inputData.getString(KEY_TRIGGER) == TRIGGER_WAKE) {
      return applyOnWake(application)
    }

    val target = inputData.getString(KEY_TARGET)
      ?.let { runCatching { ApplyTarget.valueOf(it) }.getOrNull() }
      ?: return Result.failure()

    return try {
      // Any outcome still advances the daily chain, otherwise a single empty
      // or unsupported run would silently stop the schedule for good.
      application.container.applier.applyNext(target, MIN_TRIGGER_GAP_MS)
      application.container.scheduler.scheduleNext(
        target,
        application.container.repository.ensureLoaded(),
      )
      Result.success()
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      if (runAttemptCount < MAX_ATTEMPTS) {
        Result.retry()
      } else {
        runCatching {
          application.container.scheduler.scheduleNext(
            target,
            application.container.repository.ensureLoaded(),
          )
        }
        Result.failure()
      }
    }
  }

  /**
   * Advances every target that still uses the wake schedule. One worker handles
   * both targets so two bitmap renders never run at the same time.
   */
  private suspend fun applyOnWake(application: WallCarouselApplication): Result {
    return try {
      val state = application.container.repository.ensureLoaded()
      for (target in listOf(ApplyTarget.HOME, ApplyTarget.LOCK)) {
        if (state.config(target).schedule.wake) {
          application.container.applier.applyNext(target, MIN_TRIGGER_GAP_MS)
        }
      }
      Result.success()
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }
  }

  companion object {
    const val KEY_TARGET = "target"
    const val KEY_TRIGGER = "trigger"
    const val TRIGGER_WAKE = "wake"

    /**
     * Scheduled triggers that fire almost together must not advance the same
     * target twice. Manual applies and Quick Settings tiles are unaffected.
     */
    const val MIN_TRIGGER_GAP_MS = 15_000L
    private const val MAX_ATTEMPTS = 3
  }
}
