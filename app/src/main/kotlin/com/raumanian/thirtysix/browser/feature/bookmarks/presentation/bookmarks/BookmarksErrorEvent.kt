package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkUrlValidationError
/** Mapped from `BookmarkException`; `BookmarksScreen` resolves the localized message. */
sealed class BookmarksErrorEvent {
    data class InvalidUrl(val reason: BookmarkUrlValidationError) : BookmarksErrorEvent()
    data object EmptyTitle : BookmarksErrorEvent()
    data object EmptyUrl : BookmarksErrorEvent()
    data object EmptyFolderName : BookmarksErrorEvent()
    data object FolderCycle : BookmarksErrorEvent()
    data object BookmarkNotFound : BookmarksErrorEvent()
    data object FolderNotFound : BookmarksErrorEvent()
}
