package com.raumanian.thirtysix.browser.feature.settings.data.mapper

import androidx.datastore.preferences.core.Preferences
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.StorageKeys
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import javax.inject.Inject

/**
 * Missing or unknown stored values decode to the defaults, so a corrupt value or one written by a
 * newer build can never yield an out-of-set retention window or text size.
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
        textSize = TextSize.fromPercentOrDefault(prefs[StorageKeys.TEXT_ZOOM_PERCENT]),
        isSwipeNavigationEnabled = prefs[StorageKeys.SWIPE_NAVIGATION_ENABLED]
            ?: AppDefaults.SWIPE_NAVIGATION_ENABLED,
    )
}
