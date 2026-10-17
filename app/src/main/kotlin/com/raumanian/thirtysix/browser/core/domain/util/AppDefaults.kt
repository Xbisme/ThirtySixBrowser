package com.raumanian.thirtysix.browser.core.domain.util

import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
/**
 * Defaults for user-overridable settings. There is no language default: the platform's
 * per-app language setting owns it.
 */
object AppDefaults {
    val THEME_MODE: ThemeMode = ThemeMode.System
    val SEARCH_ENGINE: SearchEngine = SearchEngine.Google
    const val IS_ONBOARDING_COMPLETED: Boolean = false

    // Off so new users see the brand violet; only an unset key falls back to this.
    const val DYNAMIC_COLOR_ENABLED: Boolean = false

    const val SWIPE_NAVIGATION_ENABLED: Boolean = true

    const val TAB_SWIPE_ENABLED: Boolean = true

    val HISTORY_RETENTION: HistoryRetention = HistoryRetention.Days90

    val TEXT_SIZE: TextSize = TextSize.Percent100
}
