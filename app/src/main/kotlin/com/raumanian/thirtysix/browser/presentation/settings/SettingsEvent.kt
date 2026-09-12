package com.raumanian.thirtysix.browser.presentation.settings

/**
 * Spec 016 — one-shot outcomes the Settings screen surfaces as snackbars (data-model §6).
 *
 * Delivered on `SettingsViewModel.events`, never held in state, so a message is shown once
 * and never replayed to a Composable that re-subscribes after a configuration change.
 */
sealed interface SettingsEvent {

    /** FR-043 — a settings write failed; the previous value is still in effect. */
    data object SettingNotSaved : SettingsEvent

    /** FR-043 — the platform rejected a language change; the previous language still applies. */
    data object LanguageNotApplied : SettingsEvent

    /** FR-033 — every selected category was cleared. */
    data object BrowsingDataCleared : SettingsEvent

    /** FR-033 — at least one selected category could not be cleared; the others were. */
    data object BrowsingDataPartiallyCleared : SettingsEvent

    /**
     * FR-022, FR-043 — the new retention window was saved, but older history could not be
     * deleted now; the start-up sweep removes it next launch.
     */
    data object RetentionPruneDeferred : SettingsEvent
}
