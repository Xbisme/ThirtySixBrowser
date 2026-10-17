package com.raumanian.thirtysix.browser.core.domain.model

/** Bookmarks and folders as one tree, the shape a bookmark file carries. */
sealed interface BookmarkTreeNode {
    data class Folder(val name: String, val children: List<BookmarkTreeNode>) : BookmarkTreeNode

    data class Link(val title: String, val url: String, val addedAtMillis: Long?) : BookmarkTreeNode
}
