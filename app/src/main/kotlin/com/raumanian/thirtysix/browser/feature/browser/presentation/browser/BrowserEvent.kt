package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent

sealed interface BrowserEvent {
    /** Hide the keyboard, clear focus, then load, in that order. */
    data class LoadUrl(val url: String) : BrowserEvent

    /** The active tab's page mode flipped; the WebView reloads in it. */
    data class SetDesktopMode(val enabled: Boolean) : BrowserEvent

    data class TabLimit(val error: TabsErrorEvent) : BrowserEvent

    data class Bookmark(val event: BookmarkSnackbarEvent) : BrowserEvent
}
