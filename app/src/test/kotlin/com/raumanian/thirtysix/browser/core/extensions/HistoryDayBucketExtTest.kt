package com.raumanian.thirtysix.browser.core.extensions

import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 014 R2 — pure-function bucketer.
 */
class HistoryDayBucketExtTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 5, 8)

    @Test
    fun `same-date as today returns Today`() {
        val visitedAt = ZonedDateTime.of(today.atTime(14, 30), zone).toInstant().toEpochMilli()
        assertEquals(HistoryDayBucket.Today, historyDayBucketOf(visitedAt, today, zone))
    }

    @Test
    fun `start-of-day today returns Today`() {
        val visitedAt = ZonedDateTime.of(today.atStartOfDay(), zone).toInstant().toEpochMilli()
        assertEquals(HistoryDayBucket.Today, historyDayBucketOf(visitedAt, today, zone))
    }

    @Test
    fun `one day before today returns Yesterday`() {
        val yesterday = today.minusDays(1L)
        val visitedAt = ZonedDateTime.of(yesterday.atTime(23, 59), zone).toInstant().toEpochMilli()
        assertEquals(HistoryDayBucket.Yesterday, historyDayBucketOf(visitedAt, today, zone))
    }

    @Test
    fun `two days before today returns OnDate with that date`() {
        val twoDaysAgo = today.minusDays(2L)
        val visitedAt = ZonedDateTime.of(twoDaysAgo.atTime(10, 0), zone).toInstant().toEpochMilli()
        val result = historyDayBucketOf(visitedAt, today, zone)
        assertTrue(result is HistoryDayBucket.OnDate)
        assertEquals(twoDaysAgo, (result as HistoryDayBucket.OnDate).date)
    }

    @Test
    fun `one year ago returns OnDate with that date`() {
        val longAgo = today.minusDays(365L)
        val visitedAt = ZonedDateTime.of(longAgo.atTime(12, 0), zone).toInstant().toEpochMilli()
        val result = historyDayBucketOf(visitedAt, today, zone)
        assertTrue(result is HistoryDayBucket.OnDate)
        assertEquals(longAgo, (result as HistoryDayBucket.OnDate).date)
    }

    @Test
    fun `bucket follows the supplied zone for boundary visits`() {
        // 2026-05-08 23:30 UTC vs same wall-clock interpreted in UTC+5 → 2026-05-09 04:30 local
        val visitedAt = Instant.parse("2026-05-08T23:30:00Z").toEpochMilli()
        val plus5 = ZoneId.of("UTC+5")
        val todayInPlus5 = LocalDate.of(2026, 5, 9)
        assertEquals(HistoryDayBucket.Today, historyDayBucketOf(visitedAt, todayInPlus5, plus5))
        assertEquals(HistoryDayBucket.Today, historyDayBucketOf(visitedAt, today, zone))
    }
}
