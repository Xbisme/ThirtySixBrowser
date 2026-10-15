package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent

sealed interface PageActionsEvent {
    /** The active tab's page mode flipped; the WebView reloads in it. */
    data class SetDesktopMode(val enabled: Boolean) : PageActionsEvent

    data class TabLimit(val error: TabsErrorEvent) : PageActionsEvent

    data class Bookmark(val event: BookmarkSnackbarEvent) : PageActionsEvent
}
