package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Spec 013 FR-014 — emits the breadcrumb chain (root → leaf) for the
 * currently displayed folder. Empty list when the user is at root.
 *
 * NOTE: simple snapshot via the folder DAO — does NOT re-emit when an
 * ancestor is renamed. For v1.0 this is acceptable (renames are rare; a
 * navigation event refreshes the chain on the next folder change). A future
 * upgrade could observe the folder table and recompute on every emission.
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
