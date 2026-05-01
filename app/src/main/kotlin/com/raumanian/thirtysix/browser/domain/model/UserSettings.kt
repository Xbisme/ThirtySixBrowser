package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults

/**
 * Immutable snapshot of all user preferences (Spec 006 FR-010).
 *
 * Consumers observe the full snapshot via SettingsRepository.observeSettings()
 * and branch on individual fields. New emissions are produced when any single
 * key changes; the repository applies distinctUntilChanged so identical
 * snapshots collapse.
 */
data class UserSettings(
    val themeMode: ThemeMode,
    val languageOverride: LanguageOverride,
    val searchEngine: SearchEngine,
    val isOnboardingCompleted: Boolean,
) {
    companion object {
        /**
         * Documented first-launch defaults. Used as the initial value passed to
         * `collectAsStateWithLifecycle(...)` so MainActivity composes a visually
         * correct first frame without blocking on disk I/O (Spec 006 SC-002).
         */
        val DEFAULT = UserSettings(
            themeMode = AppDefaults.THEME_MODE,
            languageOverride = AppDefaults.LANGUAGE_OVERRIDE,
            searchEngine = AppDefaults.SEARCH_ENGINE,
            isOnboardingCompleted = AppDefaults.IS_ONBOARDING_COMPLETED,
        )
    }
}
