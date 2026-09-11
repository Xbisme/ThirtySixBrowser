package com.raumanian.thirtysix.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 016 T011 — [HistoryRetention] carries exactly the four FR-020 windows, orders them
 * for the FR-021 warning, and decodes any stored value it does not recognise to 90 days.
 */
class HistoryRetentionTest {

    @Test
    fun `the four windows carry 7, 30, 90 and 180 days`() {
        assertEquals(listOf(7, 30, 90, 180), HistoryRetention.entries.map { it.days })
    }

    @Test
    fun `isShorterThan is true only for a strictly smaller window`() {
        assertTrue(HistoryRetention.Days7.isShorterThan(HistoryRetention.Days30))
        assertTrue(HistoryRetention.Days90.isShorterThan(HistoryRetention.Days180))
        assertFalse("equal is not shorter (FR-006)", HistoryRetention.Days90.isShorterThan(HistoryRetention.Days90))
        assertFalse(HistoryRetention.Days180.isShorterThan(HistoryRetention.Days7))
    }

    @Test
    fun `fromDaysOrDefault maps each exact day count to its window`() {
        HistoryRetention.entries.forEach { window ->
            assertEquals(window, HistoryRetention.fromDaysOrDefault(window.days))
        }
    }

    @Test
    fun `fromDaysOrDefault maps anything outside the set to 90 days`() {
        listOf(null, 0, -1, 45, 365).forEach { stored ->
            assertEquals("stored=$stored", HistoryRetention.Days90, HistoryRetention.fromDaysOrDefault(stored))
        }
    }
}
