package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.BookmarkSearchResult
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Spec 013 FR-024..028 / R3 — global substring search; each result row gets
 * its folder path (root → leaf) inline for FR-026 display.
 */
class SearchBookmarksUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(query: String): Flow<List<BookmarkSearchResult>> =
        repository.searchBookmarks(query).map { bookmarks ->
            // R3 implementation note — augment each row with its ancestor chain.
            // For 50 visible results × ~10-deep chain, this is ~500 reads per emission.
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
