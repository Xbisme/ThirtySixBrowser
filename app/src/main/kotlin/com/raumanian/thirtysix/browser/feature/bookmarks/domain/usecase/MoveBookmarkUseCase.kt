package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Moves a bookmark to a new folder (null = root). */
class MoveBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(bookmarkId: Long, newParentId: Long?): Result<Unit> =
        repository.moveBookmarkToFolder(bookmarkId = bookmarkId, newParentId = newParentId)
}
