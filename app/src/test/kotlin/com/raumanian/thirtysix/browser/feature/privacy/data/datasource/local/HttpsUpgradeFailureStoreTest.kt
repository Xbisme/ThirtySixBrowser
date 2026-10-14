package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 023 FR-020 / Spec 024 FR-003a — failed upgrades are remembered until Fire clears them. */
class HttpsUpgradeFailureStoreTest {
    @Test
    fun `failed hosts are remembered lower-case and cleared by clear`() {
        val store = HttpsUpgradeFailureStore()
        store.markFailed("NeverSSL.com")
        store.markFailed("http.badssl.com")

        assertEquals(setOf("neverssl.com", "http.badssl.com"), store.snapshot().toSet())

        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }
}
