package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Deletes a folder and everything under it. */
class DeleteFolderUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long): Result<BookmarkDescendantCount> =
        repository.deleteFolderCascade(folderId)
}
