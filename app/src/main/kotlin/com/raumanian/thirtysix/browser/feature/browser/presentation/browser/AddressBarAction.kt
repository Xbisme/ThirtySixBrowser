package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** The address bar, the Home page's search field and the way back to Home. */
sealed interface AddressBarAction {
    data class TextChange(val text: String) : AddressBarAction

    data class FocusChange(val focused: Boolean) : AddressBarAction

    data object Submit : AddressBarAction

    data object Clear : AddressBarAction

    data object HomePressed : AddressBarAction

    data object HomeOverlayDismissed : AddressBarAction

    data class HomeSubmit(val input: String) : AddressBarAction

    data object NavigateFromHome : AddressBarAction

    data object BackWithoutHistory : AddressBarAction
}
