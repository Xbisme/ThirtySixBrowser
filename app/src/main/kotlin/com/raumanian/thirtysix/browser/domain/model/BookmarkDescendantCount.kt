package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 013 — total bookmarks + folders below a given folder, used to
 * populate the "Delete folder?" confirmation dialog body (FR-020).
 */
data class BookmarkDescendantCount(
    val bookmarks: Int,
    val folders: Int,
) {
    val total: Int get() = bookmarks + folders
}
