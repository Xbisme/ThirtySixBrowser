package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject

/** Spec 013 FR-019 / FR-020 — cascade-deletes a folder + every descendant (R7). */
class DeleteFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long): Result<BookmarkDescendantCount> =
        repository.deleteFolderCascade(folderId)
}
