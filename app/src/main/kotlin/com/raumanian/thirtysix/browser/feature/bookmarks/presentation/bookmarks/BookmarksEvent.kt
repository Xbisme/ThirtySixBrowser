package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

sealed interface BookmarksEvent {
    data class OpenUrl(val url: String) : BookmarksEvent

    data class Error(val error: BookmarksErrorEvent) : BookmarksEvent
}
