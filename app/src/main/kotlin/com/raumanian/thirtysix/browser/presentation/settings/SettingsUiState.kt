package com.raumanian.thirtysix.browser.presentation.settings

import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode

/**
 * Spec 016 — immutable state of the Settings screen (data-model §6).
 *
 * The settings-backed fields mirror the latest `UserSettings` snapshot and start at the
 * documented defaults, so the first frame is correct before the snapshot arrives. The two
 * platform facts — [isDynamicColorSupported] and [appVersionName] — are injected once and
 * never change for the life of the view model. [appLanguage] is read from the platform, never
 * from settings, and refreshed each time the screen starts (research R12).
 */
data class SettingsUiState(
    val themeMode: ThemeMode = AppDefaults.THEME_MODE,
    val isDynamicColorEnabled: Boolean = AppDefaults.DYNAMIC_COLOR_ENABLED,
    val isDynamicColorSupported: Boolean = false,
    val searchEngine: SearchEngine = AppDefaults.SEARCH_ENGINE,
    val historyRetention: HistoryRetention = AppDefaults.HISTORY_RETENTION,
    val appLanguage: AppLanguage = AppLanguage.FollowSystem,
    val appVersionName: String = "",
    /** The single open dialog, or `null` when none is — at most one is ever open. */
    val dialog: SettingsDialog? = null,
)

/**
 * Spec 016 — which dialog the Settings screen is showing (data-model §6). Each user story
 * adds the variants it needs.
 */
sealed interface SettingsDialog {

    /** US1 — Light / Dark / System. */
    data object ThemeChooser : SettingsDialog

    /** US2 — Google / DuckDuckGo / Bing. */
    data object SearchEngineChooser : SettingsDialog

    /** US3 — Follow system plus the eight languages. */
    data object LanguageChooser : SettingsDialog

    /**
     * US4 — what to clear. [selection] starts with all three categories (FR-025). While
     * [inProgress] the dialog can be neither confirmed again nor dismissed (FR-032).
     */
    data class ClearBrowsingData(
        val selection: Set<ClearBrowsingDataCategory>,
        val inProgress: Boolean,
    ) : SettingsDialog

    /** US5 — 7 / 30 / 90 / 180 days. */
    data object RetentionChooser : SettingsDialog

    /** US5 FR-021 — the warning before a shorter window deletes history. */
    data class ConfirmShortenRetention(val target: HistoryRetention) : SettingsDialog
}
