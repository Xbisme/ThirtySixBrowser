package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

/** One-shot snackbar outcomes, never held in state so a re-subscribing screen does not replay them. */
sealed interface SettingsEvent {

    data object SettingNotSaved : SettingsEvent

    data object LanguageNotApplied : SettingsEvent

    data object BrowsingDataCleared : SettingsEvent

    data object BrowsingDataPartiallyCleared : SettingsEvent

    /** The window was saved; the start-up sweep removes the older history next launch. */
    data object RetentionPruneDeferred : SettingsEvent
}
