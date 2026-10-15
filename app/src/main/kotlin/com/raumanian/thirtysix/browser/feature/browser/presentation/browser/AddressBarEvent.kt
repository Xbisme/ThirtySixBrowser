package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface AddressBarEvent {
    /** Hide the keyboard, clear focus, then load, in that order. */
    data class LoadUrl(val url: String) : AddressBarEvent
}
