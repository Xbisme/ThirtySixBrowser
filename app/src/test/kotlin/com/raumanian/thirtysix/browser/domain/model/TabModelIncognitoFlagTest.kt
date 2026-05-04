package com.raumanian.thirtysix.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 012 — T011 smoke test.
 *
 * Verifies that the `isIncognito` property added in Phase 2 (T004) defaults to
 * `false` so all Spec 011 fixtures and tests stay backwards-compatible, and
 * that an explicit `isIncognito = true` participates in equality the same way
 * any other `data class` field does.
 */
class TabModelIncognitoFlagTest {

    private fun newTab(isIncognito: Boolean? = null): Tab {
        val base = Tab(
            id = 1L,
            url = "https://example.org/",
            title = "Example",
            position = 0,
            createdAt = 1_700_000_000_000L,
            lastActiveAt = 1_700_000_500_000L,
        )
        return if (isIncognito == null) base else base.copy(isIncognito = isIncognito)
    }

    @Test
    fun `isIncognito defaults to false`() {
        assertFalse(newTab().isIncognito)
    }

    @Test
    fun `default-flag tab equals legacy-style construction (Spec 011 backwards compat)`() {
        val legacy = newTab()
        val reconstructed = Tab(
            id = 1L,
            url = "https://example.org/",
            title = "Example",
            position = 0,
            createdAt = 1_700_000_000_000L,
            lastActiveAt = 1_700_000_500_000L,
        )
        assertEquals(legacy, reconstructed)
    }

    @Test
    fun `incognito tab is not equal to a normal tab with otherwise identical fields`() {
        val normal = newTab(isIncognito = false)
        val incognito = newTab(isIncognito = true)
        assertNotEquals(normal, incognito)
        assertTrue(incognito.isIncognito)
        assertFalse(normal.isIncognito)
    }
}
