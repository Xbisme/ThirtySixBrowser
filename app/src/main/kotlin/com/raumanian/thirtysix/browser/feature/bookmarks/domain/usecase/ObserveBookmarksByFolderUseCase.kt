package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Folders and bookmarks directly under one folder, in one flow. */
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
