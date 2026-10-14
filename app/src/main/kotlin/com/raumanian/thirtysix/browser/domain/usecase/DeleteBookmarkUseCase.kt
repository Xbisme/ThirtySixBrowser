package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/**
 * Spec 013 FR-023 — deletes a single bookmark by id. Trivial wrapper; exists
 * to keep the use-case abstraction consistent across the screen layer.
 */
class DeleteBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> =
        repository.deleteBookmark(id)
}
