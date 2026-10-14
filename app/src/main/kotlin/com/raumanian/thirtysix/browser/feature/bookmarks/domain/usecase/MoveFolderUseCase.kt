package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Moves a folder to a new parent (null = root); the repository rejects cycles. */
class MoveFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long, newParentId: Long?): Result<Unit> =
        repository.moveFolder(folderId = folderId, newParentId = newParentId)
}
