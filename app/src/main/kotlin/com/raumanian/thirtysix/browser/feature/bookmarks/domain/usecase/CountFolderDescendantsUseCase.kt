package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import javax.inject.Inject

/** Counts everything under a folder, for the delete-folder confirmation. */
class CountFolderDescendantsUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(folderId: Long): BookmarkDescendantCount =
        repository.countDescendants(folderId)
}
