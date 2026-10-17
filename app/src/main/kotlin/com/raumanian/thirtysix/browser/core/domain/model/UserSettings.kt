package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
// No language field: the platform's per-app language setting owns it, and a copy would go stale.
data class UserSettings(
    val themeMode: ThemeMode,
    val isDynamicColorEnabled: Boolean,
    val searchEngine: SearchEngine,
    val historyRetention: HistoryRetention,
    val isOnboardingCompleted: Boolean,
    val textSize: TextSize = AppDefaults.TEXT_SIZE,
    val isSwipeNavigationEnabled: Boolean = AppDefaults.SWIPE_NAVIGATION_ENABLED,
    val isTabSwipeEnabled: Boolean = AppDefaults.TAB_SWIPE_ENABLED,
) {
    companion object {
        // Initial value while settings load, so the first frame needs no disk read. Its onboarding
        // flag is not the stored one: never route from it.
        val DEFAULT = UserSettings(
            themeMode = AppDefaults.THEME_MODE,
            isDynamicColorEnabled = AppDefaults.DYNAMIC_COLOR_ENABLED,
            searchEngine = AppDefaults.SEARCH_ENGINE,
            historyRetention = AppDefaults.HISTORY_RETENTION,
            isOnboardingCompleted = AppDefaults.IS_ONBOARDING_COMPLETED,
            textSize = AppDefaults.TEXT_SIZE,
            isSwipeNavigationEnabled = AppDefaults.SWIPE_NAVIGATION_ENABLED,
            isTabSwipeEnabled = AppDefaults.TAB_SWIPE_ENABLED,
        )
    }
}
