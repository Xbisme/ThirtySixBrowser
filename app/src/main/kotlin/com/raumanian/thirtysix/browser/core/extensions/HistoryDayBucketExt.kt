package com.raumanian.thirtysix.browser.core.extensions

import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Spec 014 R2 — pure-function bucketer for grouping a history entry's `visitedAt` epoch
 * milliseconds into a [HistoryDayBucket] relative to a reference `today` date.
 *
 * Locale-agnostic and zone-aware: callers pass the zone they want to compute against
 * (typically [ZoneId.systemDefault] at view-render time). Equality between [Instant]'s
 * resolved [LocalDate] and the reference today/yesterday determines the bucket.
 */
fun historyDayBucketOf(
    visitedAt: Long,
    today: LocalDate,
    zone: ZoneId,
): HistoryDayBucket {
    val date = Instant.ofEpochMilli(visitedAt).atZone(zone).toLocalDate()
    return when (date) {
        today -> HistoryDayBucket.Today
        today.minusDays(1L) -> HistoryDayBucket.Yesterday
        else -> HistoryDayBucket.OnDate(date)
    }
}
