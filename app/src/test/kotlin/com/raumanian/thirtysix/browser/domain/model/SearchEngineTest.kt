package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 010 — Verifies the [SearchEngine] enum extension (Google + DuckDuckGo
 * + Bing) and the unchanged `fromStorageValueOrDefault(...)` semantics.
 */
class SearchEngineTest {

    @Test
    fun has_three_values() {
        assertEquals(3, SearchEngine.entries.size)
    }

    @Test
    fun contains_Google_DuckDuckGo_Bing() {
        val values = SearchEngine.entries.toSet()
        assertTrue(values.contains(SearchEngine.Google))
        assertTrue(values.contains(SearchEngine.DuckDuckGo))
        assertTrue(values.contains(SearchEngine.Bing))
    }

    @Test
    fun storageValue_roundTrips_via_fromStorageValueOrDefault() {
        SearchEngine.entries.forEach { engine ->
            assertEquals(engine, SearchEngine.fromStorageValueOrDefault(engine.storageValue))
        }
    }

    @Test
    fun fromStorageValueOrDefault_googleString_returnsGoogle() {
        assertEquals(SearchEngine.Google, SearchEngine.fromStorageValueOrDefault("google"))
    }

    @Test
    fun fromStorageValueOrDefault_duckduckgoString_returnsDuckDuckGo() {
        assertEquals(SearchEngine.DuckDuckGo, SearchEngine.fromStorageValueOrDefault("duckduckgo"))
    }

    @Test
    fun fromStorageValueOrDefault_bingString_returnsBing() {
        assertEquals(SearchEngine.Bing, SearchEngine.fromStorageValueOrDefault("bing"))
    }

    @Test
    fun fromStorageValueOrDefault_unknownValue_returnsDefault() {
        assertEquals(AppDefaults.SEARCH_ENGINE, SearchEngine.fromStorageValueOrDefault("yandex"))
        assertEquals(SearchEngine.Google, SearchEngine.fromStorageValueOrDefault("yandex"))
    }

    @Test
    fun fromStorageValueOrDefault_null_returnsDefault() {
        assertEquals(AppDefaults.SEARCH_ENGINE, SearchEngine.fromStorageValueOrDefault(null))
    }

    @Test
    fun fromStorageValueOrDefault_emptyString_returnsDefault() {
        assertEquals(AppDefaults.SEARCH_ENGINE, SearchEngine.fromStorageValueOrDefault(""))
    }
}
