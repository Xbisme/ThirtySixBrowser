package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Spec 013 — combined Flow that emits both folders and bookmarks under the
 * current folder (FR-008 / FR-009). UI renders folders before bookmarks.
 */
class ObserveBookmarksByFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(folderId: Long?): Flow<BookmarksByFolder> =
        combine(
            repository.observeFoldersByParent(folderId),
            repository.observeBookmarksByFolder(folderId),
        ) { folders, bookmarks ->
            BookmarksByFolder(folders = folders, bookmarks = bookmarks)
        }
}

data class BookmarksByFolder(
    val folders: List<BookmarkFolder>,
    val bookmarks: List<Bookmark>,
)
