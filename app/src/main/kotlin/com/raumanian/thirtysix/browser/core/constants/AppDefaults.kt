package com.raumanian.thirtysix.browser.core.constants

import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode

/**
 * Application-wide default values for user-overridable settings (Spec 006 FR-018).
 *
 * Per Constitution §III No-Hardcode Rule: defaults live HERE, not inline at call
 * sites. Both [com.raumanian.thirtysix.browser.domain.model.UserSettings.DEFAULT]
 * and [com.raumanian.thirtysix.browser.data.mapper.SettingsMapper] read from
 * this object so a single edit propagates everywhere.
 *
 * Spec 016 removed the language default: the app language is held by the platform's
 * per-app language setting, never by the app (FR-017).
 */
object AppDefaults {
    val THEME_MODE: ThemeMode = ThemeMode.System
    val SEARCH_ENGINE: SearchEngine = SearchEngine.Google
    const val IS_ONBOARDING_COMPLETED: Boolean = false

    /**
     * Spec 016 FR-009 — dynamic color stays on by default. It is already in effect for every
     * Android 12+ user, so existing installs see no change (spec A11).
     */
    const val DYNAMIC_COLOR_ENABLED: Boolean = true

    /** Spec 016 FR-020 — the window Spec 014 shipped as a fixed constant. */
    val HISTORY_RETENTION: HistoryRetention = HistoryRetention.Days90
}
