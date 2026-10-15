package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The breadcrumb chain (root first) for a folder; empty at the root. A snapshot: renaming an
 * ancestor shows on the next folder change, not immediately.
 */
class ObserveFolderPathUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(folderId: Long?): Flow<List<BookmarkFolder>> = flow {
        if (folderId == null) {
            emit(emptyList())
        } else {
            emit(repository.getAncestorChain(folderId))
        }
    }
}
