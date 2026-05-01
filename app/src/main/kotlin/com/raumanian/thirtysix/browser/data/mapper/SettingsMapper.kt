package com.raumanian.thirtysix.browser.data.mapper

import androidx.datastore.preferences.core.Preferences
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
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
 * Per Spec 006 Q2: language stored as nullable String on disk; absent OR null
 * OR blank decode to [LanguageOverride.FollowSystem]. Non-blank decodes to
 * [LanguageOverride.Explicit].
 */
class SettingsMapper @Inject constructor() {

    fun toDomain(prefs: Preferences): UserSettings = UserSettings(
        themeMode = ThemeMode.fromStorageValueOrDefault(prefs[StorageKeys.THEME_MODE]),
        languageOverride = prefs[StorageKeys.LANGUAGE_OVERRIDE].toLanguageOverride(),
        searchEngine = SearchEngine.fromStorageValueOrDefault(prefs[StorageKeys.SEARCH_ENGINE]),
        isOnboardingCompleted = prefs[StorageKeys.IS_ONBOARDING_COMPLETED]
            ?: AppDefaults.IS_ONBOARDING_COMPLETED,
    )

    private fun String?.toLanguageOverride(): LanguageOverride =
        if (this.isNullOrBlank()) {
            LanguageOverride.FollowSystem
        } else {
            LanguageOverride.Explicit(this)
        }
}
