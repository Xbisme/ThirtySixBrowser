package com.raumanian.thirtysix.browser.data.mapper

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [SettingsMapper]. Builds in-memory MutablePreferences
 * fixtures via mutablePreferencesOf() — no DataStore needed.
 */
class SettingsMapperTest {

    private val mapper = SettingsMapper()

    // ----------------------------- US2: defaults --------------------------

    @Test
    fun emptyPrefs_returnsAllDefaults() {
        val snapshot = mapper.toDomain(mutablePreferencesOf())
        assertEquals(AppDefaults.THEME_MODE, snapshot.themeMode)
        assertEquals(AppDefaults.LANGUAGE_OVERRIDE, snapshot.languageOverride)
        assertEquals(AppDefaults.SEARCH_ENGINE, snapshot.searchEngine)
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

    // ----------------------------- US3: language sealed type --------------

    @Test
    fun nullLanguage_isFollowSystem() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        // Key absent → mapper sees null → FollowSystem
        assertEquals(LanguageOverride.FollowSystem, mapper.toDomain(prefs).languageOverride)
    }

    @Test
    fun blankLanguage_isFollowSystem() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.LANGUAGE_OVERRIDE] = "   "
        assertEquals(LanguageOverride.FollowSystem, mapper.toDomain(prefs).languageOverride)
    }

    @Test
    fun explicitLanguage_isExplicit() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.LANGUAGE_OVERRIDE] = "de"
        assertEquals(LanguageOverride.Explicit("de"), mapper.toDomain(prefs).languageOverride)
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
        prefs[StorageKeys.LANGUAGE_OVERRIDE] = "vi"
        prefs[StorageKeys.SEARCH_ENGINE] = SearchEngine.Google.storageValue
        prefs[StorageKeys.IS_ONBOARDING_COMPLETED] = true

        val snapshot = mapper.toDomain(prefs)
        assertEquals(ThemeMode.Dark, snapshot.themeMode)
        assertEquals(LanguageOverride.Explicit("vi"), snapshot.languageOverride)
        assertEquals(SearchEngine.Google, snapshot.searchEngine)
        assertEquals(true, snapshot.isOnboardingCompleted)
    }
}
