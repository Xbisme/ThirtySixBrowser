package com.raumanian.thirtysix.browser.feature.history.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Callers pass the zone to compute against, typically [ZoneId.systemDefault] at render time.
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
