// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure helpers that translate a [Schedule] into a delay from now. */
object SchedulePlanner {
  /** Minimum delay accepted by WorkManager for periodic work. */
  const val MIN_INTERVAL_MINUTES = 15

  /**
   * Delay in milliseconds until the next daily trigger, or null when no daily
   * times are configured. Interval and wake triggers do not use this helper:
   * the first is periodic work, the second is driven by the unlock watcher.
   */
  fun nextDelayMillis(
    schedule: Schedule,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
  ): Long? {
    val next = nextDailyTrigger(
      schedule.dailyMinutes,
      Instant.ofEpochMilli(nowMillis).atZone(zone),
    ) ?: return null
    return next.toInstant().toEpochMilli().minus(nowMillis).coerceAtLeast(0L)
  }

  /** Next occurrence of any of [times] (minutes from midnight) strictly after [now]. */
  fun nextDailyTrigger(times: List<Int>, now: ZonedDateTime): ZonedDateTime? {
    val valid = times.filter { it in 0 until 24 * 60 }.distinct().sorted()
    if (valid.isEmpty()) return null

    for (minuteOfDay in valid) {
      val candidate = now.toLocalDate()
        .atStartOfDay(now.zone)
        .plusMinutes(minuteOfDay.toLong())
      if (candidate.isAfter(now)) return candidate
    }

    return now.toLocalDate()
      .plusDays(1)
      .atStartOfDay(now.zone)
      .plusMinutes(valid.first().toLong())
  }
}
