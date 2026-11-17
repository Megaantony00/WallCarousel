// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchedulePlannerTest {
  private val zone = ZoneId.of("UTC")

  @Test
  fun dailySchedulePicksTheNextTimeToday() {
    val now = ZonedDateTime.of(2026, 11, 15, 7, 30, 0, 0, zone)
    val delay = SchedulePlanner.nextDelayMillis(
      schedule = Schedule(dailyMinutes = listOf(8 * 60, 20 * 60)),
      nowMillis = now.toInstant().toEpochMilli(),
      zone = zone,
    )

    assertEquals(30 * 60_000L, delay)
  }

  @Test
  fun dailyScheduleRollsOverToTomorrow() {
    val now = ZonedDateTime.of(2026, 11, 15, 21, 0, 0, 0, zone)
    val delay = SchedulePlanner.nextDelayMillis(
      schedule = Schedule(dailyMinutes = listOf(8 * 60)),
      nowMillis = now.toInstant().toEpochMilli(),
      zone = zone,
    )

    assertEquals(11 * 60 * 60_000L, delay)
  }

  @Test
  fun intervalAndWakeTriggersDoNotUseTheDailyPlanner() {
    assertNull(
      SchedulePlanner.nextDelayMillis(
        schedule = Schedule(intervalMinutes = 60),
        nowMillis = 0L,
        zone = zone,
      ),
    )
    assertNull(
      SchedulePlanner.nextDelayMillis(
        schedule = Schedule(wake = true),
        nowMillis = 0L,
        zone = zone,
      ),
    )
  }

  @Test
  fun manualAndEmptyDailySchedulesDoNotTrigger() {
    assertNull(
      SchedulePlanner.nextDelayMillis(
        schedule = Schedule(),
        nowMillis = 0L,
        zone = zone,
      ),
    )
    assertNull(
      SchedulePlanner.nextDelayMillis(
        schedule = Schedule(dailyMinutes = emptyList()),
        nowMillis = 0L,
        zone = zone,
      ),
    )
  }
}
