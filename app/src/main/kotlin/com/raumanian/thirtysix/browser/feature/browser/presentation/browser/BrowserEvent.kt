package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface BrowserEvent {
    /** Hide the keyboard, clear focus, then load, in that order. */
    data class LoadUrl(val url: String) : BrowserEvent
}
