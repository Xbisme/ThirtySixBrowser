package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory
/**
 * Settings-backed fields start at the defaults, so the first frame is right before the snapshot
 * arrives. [appLanguage] comes from the platform, never from settings.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = AppDefaults.THEME_MODE,
    val isDynamicColorEnabled: Boolean = AppDefaults.DYNAMIC_COLOR_ENABLED,
    val isDynamicColorSupported: Boolean = false,
    val searchEngine: SearchEngine = AppDefaults.SEARCH_ENGINE,
    val historyRetention: HistoryRetention = AppDefaults.HISTORY_RETENTION,
    val textSize: TextSize = AppDefaults.TEXT_SIZE,
    val appLanguage: AppLanguage = AppLanguage.FollowSystem,
    val appVersionName: String = "",
    // Re-read from the system on every resume, never stored.
    val isDefaultBrowser: Boolean = false,
    val dialog: SettingsDialog? = null,
)

sealed interface SettingsDialog {

    data object ThemeChooser : SettingsDialog

    data object SearchEngineChooser : SettingsDialog

    data object LanguageChooser : SettingsDialog

    /** While [inProgress] the dialog can be neither confirmed again nor dismissed. */
    data class ClearBrowsingData(
        val selection: Set<ClearBrowsingDataCategory>,
        val inProgress: Boolean,
    ) : SettingsDialog

    data object RetentionChooser : SettingsDialog

    data object TextSizeChooser : SettingsDialog

    /** The warning before a shorter window deletes history. */
    data class ConfirmShortenRetention(val target: HistoryRetention) : SettingsDialog
}
