package com.raumanian.thirtysix.browser.feature.bookmarks.domain.model

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode

data class ParsedBookmarkFile(
    val root: BookmarkTreeNode.Folder,
    val linkCount: Int,
    val folderCount: Int,
    val duplicatesSkipped: Int,
    val unsupportedSkipped: Int,
)
