package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject

/** Spec 013 FR-021 — moves a bookmark to a new folder (null = root). */
class MoveBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(bookmarkId: Long, newParentId: Long?): Result<Unit> =
        repository.moveBookmarkToFolder(bookmarkId = bookmarkId, newParentId = newParentId)
}
