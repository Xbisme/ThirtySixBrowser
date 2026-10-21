package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkSearchResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Global substring search; each result carries its folder path for inline display. */
class SearchBookmarksUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(query: String): Flow<List<BookmarkSearchResult>> =
        combine(
            repository.searchBookmarks(query, BrowserLimits.BOOKMARK_SEARCH_MAX_RESULTS),
            repository.observeAllFolders(),
        ) { bookmarks, folders ->
            val byId = folders.associateBy { it.id }
            bookmarks.map { b -> BookmarkSearchResult(bookmark = b, folderPath = pathOf(b.parentFolderId, byId)) }
        }

    private fun pathOf(folderId: Long?, byId: Map<Long, BookmarkFolder>): List<BookmarkFolder> {
        val path = ArrayDeque<BookmarkFolder>()
        val visited = HashSet<Long>()
        var current = folderId
        while (current != null && visited.add(current)) {
            val folder = byId[current] ?: break
            path.addFirst(folder)
            current = folder.parentId
        }
        return path
    }
}
