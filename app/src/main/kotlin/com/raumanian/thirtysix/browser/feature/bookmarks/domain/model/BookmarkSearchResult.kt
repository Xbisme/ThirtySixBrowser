package com.raumanian.thirtysix.browser.feature.bookmarks.domain.model

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder

/** A bookmark with its folder path, root first; empty when the bookmark is at the root. */
data class BookmarkSearchResult(
    val bookmark: Bookmark,
    val folderPath: List<BookmarkFolder>,
)
