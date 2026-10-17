package com.raumanian.thirtysix.browser.feature.bookmarks.fake

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.repository.BookmarkFileRepository
import kotlinx.coroutines.CompletableDeferred

class FakeBookmarkFileRepository : BookmarkFileRepository {
    var readResult: BookmarkFileReadResult = BookmarkFileReadResult.ReadFailed
    var writeSucceeds: Boolean = true
    val written = mutableMapOf<String, BookmarkTreeNode.Folder>()

    /** When set, reads suspend until it completes, to observe in-progress state. */
    var readGate: CompletableDeferred<Unit>? = null

    override suspend fun read(uri: String, untitledFolderName: String): BookmarkFileReadResult {
        readGate?.await()
        return readResult
    }

    override suspend fun write(uri: String, tree: BookmarkTreeNode.Folder): Boolean {
        if (writeSucceeds) written[uri] = tree
        return writeSucceeds
    }
}
