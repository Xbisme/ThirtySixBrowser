package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Spec 013 FR-020 / R8 — counts total descendants under a folder. Drives
 * the delete-folder confirmation dialog body.
 */
class CountFolderDescendantsUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long): BookmarkDescendantCount =
        repository.countDescendants(folderId)
}
