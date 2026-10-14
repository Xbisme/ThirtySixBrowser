package com.raumanian.thirtysix.browser.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isIncognito` defaults to `false`, and an explicit `true` takes part in equality like any
 * other field.
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
    fun `default-flag tab equals legacy-style construction (backwards compat)`() {
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
