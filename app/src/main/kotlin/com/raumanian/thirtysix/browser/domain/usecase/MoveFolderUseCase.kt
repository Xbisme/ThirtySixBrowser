package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/**
 * Spec 013 FR-017 / FR-018 — moves a folder to a new parent (null = root).
 * Cycle prevention is enforced inside the repository implementation (R6).
 */
class MoveFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long, newParentId: Long?): Result<Unit> =
        repository.moveFolder(folderId = folderId, newParentId = newParentId)
}
