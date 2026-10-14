package com.raumanian.thirtysix.browser.data.local.permission

import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 024 FR-004a — Fire keeps only the one-time grants of fireproof sites. */
class OneTimeGrantStoreTest {
    @Test
    fun `retainOnly keeps the grants whose origin is accepted`() {
        val store = OneTimeGrantStore()
        store.grant(1L, 1L, "https://mail.example.com", setOf(SiteCapability.Camera))
        store.grant(2L, 1L, "https://other.test", setOf(SiteCapability.Microphone))

        store.retainOnly { origin -> origin.contains("example.com") }

        assertTrue(store.has(1L, 1L, "https://mail.example.com", SiteCapability.Camera))
        assertFalse(store.has(2L, 1L, "https://other.test", SiteCapability.Microphone))
    }
}
