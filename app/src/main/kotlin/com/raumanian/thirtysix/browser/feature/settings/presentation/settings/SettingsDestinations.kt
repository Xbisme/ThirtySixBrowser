package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.runtime.Immutable

/** Screens of other features that Settings opens; the navigation graph fills them in. */
@Immutable
data class SettingsDestinations(
    val onSitePermissions: () -> Unit = {},
    val onProtectionExceptions: () -> Unit = {},
    val onFireproofSites: () -> Unit = {},
)
