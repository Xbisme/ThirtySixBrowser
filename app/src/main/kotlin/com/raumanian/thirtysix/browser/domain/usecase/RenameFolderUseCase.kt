package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject

/** Spec 013 FR-016 — renames an existing folder. */
class RenameFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.Error(IllegalArgumentException("Folder name cannot be blank"))
        }
        val capped = trimmed.take(BrowserLimits.MAX_FOLDER_NAME_LENGTH)
        return repository.renameFolder(folderId = folderId, newName = capped)
    }
}
