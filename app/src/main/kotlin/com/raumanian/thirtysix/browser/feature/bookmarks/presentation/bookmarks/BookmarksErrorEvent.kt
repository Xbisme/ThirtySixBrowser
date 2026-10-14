package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkUrlValidationError
/**
 * Spec 013 — sealed UI-side error events. ViewModel maps domain exceptions
 * (`BookmarkException.*`) to instances of this; the screen consumes one
 * event at a time and resolves the localized message via `stringResource`
 * inside `BookmarksScreen` (mapping table lives there to keep this file
 * Android-import-free except for the model dependency).
 */
sealed class BookmarksErrorEvent {
    data class InvalidUrl(val reason: BookmarkUrlValidationError) : BookmarksErrorEvent()
    data object EmptyTitle : BookmarksErrorEvent()
    data object EmptyUrl : BookmarksErrorEvent()
    data object EmptyFolderName : BookmarksErrorEvent()
    data object FolderCycle : BookmarksErrorEvent()
    data object BookmarkNotFound : BookmarksErrorEvent()
    data object FolderNotFound : BookmarksErrorEvent()
}
