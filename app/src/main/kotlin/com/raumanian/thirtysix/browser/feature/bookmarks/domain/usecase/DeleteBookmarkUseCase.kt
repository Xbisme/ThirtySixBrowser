package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

class DeleteBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> =
        repository.deleteBookmark(id)
}
