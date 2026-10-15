package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

sealed interface ProtectionExceptionsAction {
    data class TurnOn(val site: String) : ProtectionExceptionsAction

    data object TurnAllOnClick : ProtectionExceptionsAction

    data object TurnAllOnConfirmed : ProtectionExceptionsAction

    data object TurnAllOnDismissed : ProtectionExceptionsAction
}
