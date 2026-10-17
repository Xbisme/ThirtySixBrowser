package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkExportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.repository.BookmarkFileRepository
import javax.inject.Inject

class ExportBookmarksUseCase @Inject constructor(
    private val bookmarks: BookmarkRepository,
    private val files: BookmarkFileRepository,
) {
    suspend operator fun invoke(uri: String): BookmarkExportResult {
        val tree = bookmarks.getBookmarkTree()
        return when {
            tree.children.isEmpty() -> BookmarkExportResult.NothingToExport
            !files.write(uri, tree) -> BookmarkExportResult.WriteFailed
            else -> count(tree).let { (links, folders) -> BookmarkExportResult.Exported(links, folders) }
        }
    }

    private fun count(folder: BookmarkTreeNode.Folder): Pair<Int, Int> =
        folder.children.fold(0 to 0) { (links, folders), child ->
            when (child) {
                is BookmarkTreeNode.Link -> links + 1 to folders
                is BookmarkTreeNode.Folder -> count(child).let { (l, f) -> links + l to folders + f + 1 }
            }
        }
}
