package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Spec 013 FR-012 — creates a folder under [parentId] (null = root).
 * Validates non-blank name + length cap.
 */
class CreateFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(name: String, parentId: Long?): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.Error(IllegalArgumentException("Folder name cannot be blank"))
        }
        val capped = trimmed.take(BrowserLimits.MAX_FOLDER_NAME_LENGTH)
        return repository.createFolder(name = capped, parentId = parentId)
    }
}
