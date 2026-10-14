package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** One-shot message after the star toggles a bookmark. */
sealed class BookmarkSnackbarEvent {
    data object Added : BookmarkSnackbarEvent()
    data object Removed : BookmarkSnackbarEvent()
}
