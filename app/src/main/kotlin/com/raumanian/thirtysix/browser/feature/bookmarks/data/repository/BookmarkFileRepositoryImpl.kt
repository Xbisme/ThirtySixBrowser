package com.raumanian.thirtysix.browser.feature.bookmarks.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.BookmarkFileDataSource
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.BookmarkFileDataSource.ReadOutcome
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkWriter
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.repository.BookmarkFileRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

class BookmarkFileRepositoryImpl @Inject constructor(
    private val files: BookmarkFileDataSource,
    private val parser: NetscapeBookmarkParser,
    private val writer: NetscapeBookmarkWriter,
    private val dispatchers: DispatcherProvider,
) : BookmarkFileRepository {

    override suspend fun read(uri: String, untitledFolderName: String): BookmarkFileReadResult =
        when (val outcome = files.readText(uri)) {
            ReadOutcome.Failed -> BookmarkFileReadResult.ReadFailed
            ReadOutcome.TooLarge -> BookmarkFileReadResult.TooLarge
            is ReadOutcome.Text -> withContext(dispatchers.default) {
                parser.parse(outcome.text, untitledFolderName)
                    ?.let(BookmarkFileReadResult::Parsed)
                    ?: BookmarkFileReadResult.NotABookmarkFile
            }
        }

    override suspend fun write(uri: String, tree: BookmarkTreeNode.Folder): Boolean =
        files.write(uri) { out -> writer.write(tree, out) }
}
