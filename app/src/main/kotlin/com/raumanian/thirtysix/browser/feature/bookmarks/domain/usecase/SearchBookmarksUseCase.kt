package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkSearchResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Global substring search; each result carries its folder path for inline display. */
class SearchBookmarksUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(query: String): Flow<List<BookmarkSearchResult>> =
        repository.searchBookmarks(query).map { bookmarks ->
            // One ancestor-chain read per result: ~500 reads for 50 results ten folders deep.
            bookmarks.map { b ->
                val path = if (b.parentFolderId == null) {
                    emptyList()
                } else {
                    repository.getAncestorChain(b.parentFolderId)
                }
                BookmarkSearchResult(bookmark = b, folderPath = path)
            }
        }
}
