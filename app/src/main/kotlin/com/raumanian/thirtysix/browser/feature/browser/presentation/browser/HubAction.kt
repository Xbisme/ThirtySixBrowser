package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface HubAction {
    /** "Browse without a trace": a new incognito tab, within the incognito tab limit. */
    data object NewIncognitoTab : HubAction
}
