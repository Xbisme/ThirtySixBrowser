package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

sealed interface ProtectionAction {
    data class ProtectionSwitched(val on: Boolean) : ProtectionAction

    data class FireproofSwitched(val fireproof: Boolean) : ProtectionAction
}
