// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateWakeTest {
  private val carousel = Carousel(id = "c1", name = "Test")

  private fun state(home: TargetConfig = TargetConfig(), lock: TargetConfig = TargetConfig()) =
    AppState(carousels = listOf(carousel), home = home, lock = lock)

  @Test
  fun emptyStateHasNoWakeTarget() {
    assertFalse(AppState().hasWakeTarget())
  }

  @Test
  fun wakeWithoutCarouselDoesNotStartTheWatcher() {
    val config = TargetConfig(schedule = Schedule(wake = true))
    assertFalse(state(home = config).hasWakeTarget())
  }

  @Test
  fun wakeWithCarouselStartsTheWatcher() {
    val config = TargetConfig(carouselId = carousel.id, schedule = Schedule(wake = true))
    assertTrue(state(home = config).hasWakeTarget())
  }

  @Test
  fun lockAloneCanEnableTheWatcher() {
    val config = TargetConfig(carouselId = carousel.id, schedule = Schedule(wake = true))
    assertTrue(state(lock = config).hasWakeTarget())
  }

  @Test
  fun manualOrScheduledTargetsDoNotStartTheWatcher() {
    val manual = TargetConfig(carouselId = carousel.id, schedule = Schedule())
    val daily = TargetConfig(
      carouselId = carousel.id,
      schedule = Schedule(dailyMinutes = listOf(480)),
    )
    val interval = TargetConfig(
      carouselId = carousel.id,
      schedule = Schedule(intervalMinutes = 60),
    )
    assertFalse(state(home = manual, lock = daily).hasWakeTarget())
    assertFalse(state(home = interval).hasWakeTarget())
  }

  @Test
  fun wakeCanBeCombinedWithOtherTriggers() {
    val config = TargetConfig(
      carouselId = carousel.id,
      schedule = Schedule(
        intervalMinutes = 60,
        dailyMinutes = listOf(480),
        wake = true,
      ),
    )
    assertTrue(state(home = config).hasWakeTarget())
  }
}
