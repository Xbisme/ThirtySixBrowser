package com.raumanian.thirtysix.browser.data.mapper

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [SettingsMapper]. Builds in-memory MutablePreferences
 * fixtures via mutablePreferencesOf() — no DataStore needed.
 *
 * Spec 016 removed the language cases with the language value (research R4) and added the
 * decoding rules for dynamic color and history retention (data-model §1).
 */
class SettingsMapperTest {

    private val mapper = SettingsMapper()

    // ----------------------------- US2: defaults --------------------------

    @Test
    fun emptyPrefs_returnsAllDefaults() {
        val snapshot = mapper.toDomain(mutablePreferencesOf())
        assertEquals(AppDefaults.THEME_MODE, snapshot.themeMode)
        assertEquals(AppDefaults.DYNAMIC_COLOR_ENABLED, snapshot.isDynamicColorEnabled)
        assertEquals(AppDefaults.SEARCH_ENGINE, snapshot.searchEngine)
        assertEquals(AppDefaults.HISTORY_RETENTION, snapshot.historyRetention)
        assertEquals(AppDefaults.IS_ONBOARDING_COMPLETED, snapshot.isOnboardingCompleted)
    }

    // ----------------------------- FR-009 fallback ------------------------

    @Test
    fun unknownThemeValue_fallsBackToDefault() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.THEME_MODE] = "midnight"
        assertEquals(AppDefaults.THEME_MODE, mapper.toDomain(prefs).themeMode)
    }

    @Test
    fun unknownSearchEngineValue_fallsBackToDefault() {
        // Spec 010 added Google + DuckDuckGo + Bing; this fixture uses a
        // still-unknown engine name to exercise the fallback.
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.SEARCH_ENGINE] = "yandex"
        assertEquals(AppDefaults.SEARCH_ENGINE, mapper.toDomain(prefs).searchEngine)
    }

    // ----------------------------- Spec 016: decoding rules --------------

    @Test
    fun missingDynamicColor_isTrue() {
        assertEquals(true, mapper.toDomain(mutablePreferencesOf()).isDynamicColorEnabled)
    }

    @Test
    fun storedDynamicColorFalse_isFalse() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.DYNAMIC_COLOR_ENABLED] = false
        assertEquals(false, mapper.toDomain(prefs).isDynamicColorEnabled)
    }

    @Test
    fun missingRetention_isNinetyDays() {
        assertEquals(HistoryRetention.Days90, mapper.toDomain(mutablePreferencesOf()).historyRetention)
    }

    @Test
    fun outOfSetRetention_isNinetyDays() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.HISTORY_RETENTION_DAYS] = OUT_OF_SET_RETENTION_DAYS
        assertEquals(HistoryRetention.Days90, mapper.toDomain(prefs).historyRetention)
    }

    @Test
    fun storedRetention180_isOneHundredEightyDays() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.HISTORY_RETENTION_DAYS] = HistoryRetention.Days180.days
        assertEquals(HistoryRetention.Days180, mapper.toDomain(prefs).historyRetention)
    }

    // ----------------------------- US7 / FR-019: schema rule --------------

    /**
     * Negative-path: simulate a key rename. A future build wrote the value under
     * an OLD key name (`theme_mode_old`) which the current build does not read.
     * The current build's documented behavior is "default returned, old value
     * NOT silently inherited". This test codifies that rule.
     */
    @Test
    fun renamedKey_returnsDefault_oldValueGone() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        // Old key the current code does NOT read (simulating rename)
        prefs[stringPreferencesKey("theme_mode_old")] = ThemeMode.Dark.storageValue
        // No write under the canonical StorageKeys.THEME_MODE
        val snapshot = mapper.toDomain(prefs)
        assertEquals(
            "Old key value MUST NOT silently bleed into the new key — got ${snapshot.themeMode}",
            AppDefaults.THEME_MODE,
            snapshot.themeMode,
        )
    }

    // ----------------------------- happy-path round-trip ------------------

    @Test
    fun fullPrefs_roundTripsAllFields() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.THEME_MODE] = ThemeMode.Dark.storageValue
        prefs[StorageKeys.DYNAMIC_COLOR_ENABLED] = false
        prefs[StorageKeys.SEARCH_ENGINE] = SearchEngine.Google.storageValue
        prefs[StorageKeys.HISTORY_RETENTION_DAYS] = HistoryRetention.Days30.days
        prefs[StorageKeys.IS_ONBOARDING_COMPLETED] = true

        val snapshot = mapper.toDomain(prefs)
        assertEquals(ThemeMode.Dark, snapshot.themeMode)
        assertEquals(false, snapshot.isDynamicColorEnabled)
        assertEquals(SearchEngine.Google, snapshot.searchEngine)
        assertEquals(HistoryRetention.Days30, snapshot.historyRetention)
        assertEquals(true, snapshot.isOnboardingCompleted)
    }

    private companion object {
        /** A day count that is not one of the four Spec 016 windows. */
        const val OUT_OF_SET_RETENTION_DAYS = 45
    }
}
