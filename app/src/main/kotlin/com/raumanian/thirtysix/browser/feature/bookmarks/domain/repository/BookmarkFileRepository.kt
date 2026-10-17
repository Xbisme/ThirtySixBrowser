package com.raumanian.thirtysix.browser.feature.bookmarks.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult

/** Bookmark files in the Netscape format, addressed by the content URI the file picker returned. */
interface BookmarkFileRepository {
    /** Never throws; a blank folder name in the file becomes [untitledFolderName]. */
    suspend fun read(uri: String, untitledFolderName: String): BookmarkFileReadResult

    /** Replaces the file's content; `false` when it cannot be written. */
    suspend fun write(uri: String, tree: BookmarkTreeNode.Folder): Boolean
}
