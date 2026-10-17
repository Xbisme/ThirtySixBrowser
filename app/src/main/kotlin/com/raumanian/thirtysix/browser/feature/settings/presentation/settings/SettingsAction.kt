package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory

sealed interface SettingsAction {
    data object Started : SettingsAction

    data object Resumed : SettingsAction

    data object DismissDialog : SettingsAction

    data object ThemeRowClick : SettingsAction

    data class ThemeSelected(val mode: ThemeMode) : SettingsAction

    data object TextSizeRowClick : SettingsAction

    data class TextSizeSelected(val size: TextSize) : SettingsAction

    data object SearchEngineRowClick : SettingsAction

    data class SearchEngineSelected(val engine: SearchEngine) : SettingsAction

    data object LanguageRowClick : SettingsAction

    data class LanguageSelected(val language: AppLanguage) : SettingsAction

    data object LanguageApplied : SettingsAction

    data object ClearBrowsingDataRowClick : SettingsAction

    data class ClearCategoryToggled(val category: ClearBrowsingDataCategory) : SettingsAction

    data object ClearBrowsingDataConfirmed : SettingsAction

    data object ClearBrowsingDataDismissed : SettingsAction

    data object RetentionRowClick : SettingsAction

    data class RetentionSelected(val retention: HistoryRetention) : SettingsAction

    data object ShortenRetentionConfirmed : SettingsAction

    data object ShortenRetentionCancelled : SettingsAction

    data class DynamicColorToggled(val enabled: Boolean) : SettingsAction

    data class SwipeNavigationToggled(val enabled: Boolean) : SettingsAction

    data class TabSwipeToggled(val enabled: Boolean) : SettingsAction
}
