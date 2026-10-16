package com.raumanian.thirtysix.browser.feature.settings.data.mapper

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.StorageKeys
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import com.raumanian.thirtysix.browser.feature.tabs.data.mapper.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMapperTest {

    private val mapper = SettingsMapper()

    // ----------------------------- defaults -------------------------------

    @Test
    fun emptyPrefs_returnsAllDefaults() {
        val snapshot = mapper.toDomain(mutablePreferencesOf())
        assertEquals(AppDefaults.THEME_MODE, snapshot.themeMode)
        assertEquals(AppDefaults.DYNAMIC_COLOR_ENABLED, snapshot.isDynamicColorEnabled)
        assertEquals(AppDefaults.SEARCH_ENGINE, snapshot.searchEngine)
        assertEquals(AppDefaults.HISTORY_RETENTION, snapshot.historyRetention)
        assertEquals(AppDefaults.IS_ONBOARDING_COMPLETED, snapshot.isOnboardingCompleted)
    }

    // ----------------------------- unknown-value fallback -----------------

    @Test
    fun unknownThemeValue_fallsBackToDefault() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.THEME_MODE] = "midnight"
        assertEquals(AppDefaults.THEME_MODE, mapper.toDomain(prefs).themeMode)
    }

    @Test
    fun unknownSearchEngineValue_fallsBackToDefault() {
        // An engine name this build does not know.
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.SEARCH_ENGINE] = "yandex"
        assertEquals(AppDefaults.SEARCH_ENGINE, mapper.toDomain(prefs).searchEngine)
    }

    // ----------------------------- decoding rules -------------------------

    @Test
    fun missingDynamicColor_isFalse() {
        // Only an unset key falls back to the default, which is off.
        assertEquals(false, mapper.toDomain(mutablePreferencesOf()).isDynamicColorEnabled)
    }

    @Test
    fun swipeNavigation_defaultsToOn_andReadsTheStoredValue() {
        assertEquals(true, mapper.toDomain(mutablePreferencesOf()).isSwipeNavigationEnabled)
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.SWIPE_NAVIGATION_ENABLED] = false
        assertEquals(false, mapper.toDomain(prefs).isSwipeNavigationEnabled)
    }

    @Test
    fun storedDynamicColorTrue_isTrue() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.DYNAMIC_COLOR_ENABLED] = true
        assertEquals(true, mapper.toDomain(prefs).isDynamicColorEnabled)
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

    @Test
    fun missingTextSize_isOneHundredPercent() {
        assertEquals(TextSize.Percent100, mapper.toDomain(mutablePreferencesOf()).textSize)
    }

    @Test
    fun outOfSetTextSize_isOneHundredPercent() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.TEXT_ZOOM_PERCENT] = OUT_OF_SET_TEXT_SIZE_PERCENT
        assertEquals(TextSize.Percent100, mapper.toDomain(prefs).textSize)
    }

    @Test
    fun storedTextSize150_isOneHundredFiftyPercent() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[StorageKeys.TEXT_ZOOM_PERCENT] = TextSize.Percent150.percent
        assertEquals(TextSize.Percent150, mapper.toDomain(prefs).textSize)
    }

    // ----------------------------- schema rule ----------------------------

    // A value under a key this build does not read (a rename) yields the default; it is not inherited.
    @Test
    fun renamedKey_returnsDefault_oldValueGone() {
        val prefs = mutablePreferencesOf().toMutablePreferences()
        prefs[stringPreferencesKey("theme_mode_old")] = ThemeMode.Dark.storageValue
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
        /** A day count that is not one of the four windows. */
        const val OUT_OF_SET_RETENTION_DAYS = 45
    }
}

/** A percentage that is not one of the nine text size levels. */
private const val OUT_OF_SET_TEXT_SIZE_PERCENT: Int = 130
