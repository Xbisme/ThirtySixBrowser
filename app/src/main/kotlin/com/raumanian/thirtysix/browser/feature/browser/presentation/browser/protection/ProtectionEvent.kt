package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

sealed interface ProtectionEvent {
    /** The page reloads so the new per-site setting applies. */
    data object Reload : ProtectionEvent
}
