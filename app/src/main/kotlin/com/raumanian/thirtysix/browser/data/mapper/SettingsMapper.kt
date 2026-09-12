package com.raumanian.thirtysix.browser.data.mapper

import androidx.datastore.preferences.core.Preferences
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import javax.inject.Inject

/**
 * Maps a raw [Preferences] snapshot to the typed domain [UserSettings] (Spec 006).
 *
 * Per FR-008: missing keys → documented defaults from [AppDefaults].
 * Per FR-009: unknown enum values (theme / search engine) → documented defaults
 * via the per-enum `fromStorageValueOrDefault(...)` companion methods.
 *
 * Spec 016 decoding rules (data-model §1):
 *  - missing `dynamic_color_enabled` → `true`;
 *  - missing `history_retention_days`, or any stored integer that is not exactly one of the
 *    four day counts → 90 days, via [HistoryRetention.fromDaysOrDefault]. A corrupt or
 *    future value can therefore never produce an unbounded or out-of-set window.
 */
class SettingsMapper @Inject constructor() {

    fun toDomain(prefs: Preferences): UserSettings = UserSettings(
        themeMode = ThemeMode.fromStorageValueOrDefault(prefs[StorageKeys.THEME_MODE]),
        isDynamicColorEnabled = prefs[StorageKeys.DYNAMIC_COLOR_ENABLED]
            ?: AppDefaults.DYNAMIC_COLOR_ENABLED,
        searchEngine = SearchEngine.fromStorageValueOrDefault(prefs[StorageKeys.SEARCH_ENGINE]),
        historyRetention = HistoryRetention.fromDaysOrDefault(prefs[StorageKeys.HISTORY_RETENTION_DAYS]),
        isOnboardingCompleted = prefs[StorageKeys.IS_ONBOARDING_COMPLETED]
            ?: AppDefaults.IS_ONBOARDING_COMPLETED,
    )
}
