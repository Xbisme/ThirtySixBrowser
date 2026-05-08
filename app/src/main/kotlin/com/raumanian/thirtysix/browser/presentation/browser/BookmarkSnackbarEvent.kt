package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Spec 013 — one-shot snackbar event surfaced after the star icon toggles a
 * bookmark. Cleared by `BrowserViewModel.consumeBookmarkSnackbarEvent()` once
 * the SnackbarHost has shown the message.
 *
 * Separated from Spec 011's `tabsEvent` channel to keep the two concerns
 * cleanly partitioned (mirrors Spec 012's `MaxIncognitoTabsReached` pattern).
 */
sealed class BookmarkSnackbarEvent {
    data object Added : BookmarkSnackbarEvent()
    data object Removed : BookmarkSnackbarEvent()
}
