package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults

/**
 * Immutable snapshot of all user preferences (Spec 006 FR-010).
 *
 * Consumers observe the full snapshot via SettingsRepository.observeSettings()
 * and branch on individual fields. New emissions are produced when any single
 * key changes; the repository applies distinctUntilChanged so identical
 * snapshots collapse.
 *
 * Spec 016 added [isDynamicColorEnabled] and [historyRetention], and removed the language
 * field: the app language is held by the platform's per-app language setting, which is the
 * single source of truth, so a copy here would go stale whenever the user changed the
 * language from system settings (FR-016, FR-017).
 */
data class UserSettings(
    val themeMode: ThemeMode,
    val isDynamicColorEnabled: Boolean,
    val searchEngine: SearchEngine,
    val historyRetention: HistoryRetention,
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
            isDynamicColorEnabled = AppDefaults.DYNAMIC_COLOR_ENABLED,
            searchEngine = AppDefaults.SEARCH_ENGINE,
            historyRetention = AppDefaults.HISTORY_RETENTION,
            isOnboardingCompleted = AppDefaults.IS_ONBOARDING_COMPLETED,
        )
    }
}
