package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 013 — search result row carrying a bookmark plus its folder path
 * (root → leaf, empty list when the bookmark is at the root).
 *
 * Powers FR-026 inline path display in the search results list.
 */
data class BookmarkSearchResult(
    val bookmark: Bookmark,
    val folderPath: List<BookmarkFolder>,
)
