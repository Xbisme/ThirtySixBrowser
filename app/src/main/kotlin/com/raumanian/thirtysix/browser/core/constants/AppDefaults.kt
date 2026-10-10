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
     * Spec 016 FR-009 shipped dynamic color on by default. Spec 019 (owner decision 2026-10-10)
     * turns the default OFF so new users see the ThirtySix violet; anyone who already chose a
     * value keeps it, because only an unset key falls back to this default.
     */
    const val DYNAMIC_COLOR_ENABLED: Boolean = false

    /** Spec 016 FR-020 — the window Spec 014 shipped as a fixed constant. */
    val HISTORY_RETENTION: HistoryRetention = HistoryRetention.Days90
}
