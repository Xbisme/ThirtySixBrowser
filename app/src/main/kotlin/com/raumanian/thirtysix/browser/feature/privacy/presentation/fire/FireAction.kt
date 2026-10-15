package com.raumanian.thirtysix.browser.feature.privacy.presentation.fire

sealed interface FireAction {
    data object Fire : FireAction

    data object Acknowledge : FireAction
}
