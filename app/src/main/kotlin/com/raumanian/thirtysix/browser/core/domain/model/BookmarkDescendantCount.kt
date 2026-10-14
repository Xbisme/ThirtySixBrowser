package com.raumanian.thirtysix.browser.core.domain.model

/** Everything below a folder, shown in the "Delete folder?" confirmation. */
data class BookmarkDescendantCount(
    val bookmarks: Int,
    val folders: Int,
) {
    val total: Int get() = bookmarks + folders
}
