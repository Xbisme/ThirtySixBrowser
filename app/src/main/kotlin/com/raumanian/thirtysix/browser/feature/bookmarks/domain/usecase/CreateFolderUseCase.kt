package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Creates a folder under [parentId] (null = root); the name must be non-blank and within the length cap. */
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
