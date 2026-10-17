package com.raumanian.thirtysix.browser.feature.bookmarks.domain.model

sealed interface BookmarkFileReadResult {
    data class Parsed(val file: ParsedBookmarkFile) : BookmarkFileReadResult

    data object NotABookmarkFile : BookmarkFileReadResult

    data object TooLarge : BookmarkFileReadResult

    data object ReadFailed : BookmarkFileReadResult
}
