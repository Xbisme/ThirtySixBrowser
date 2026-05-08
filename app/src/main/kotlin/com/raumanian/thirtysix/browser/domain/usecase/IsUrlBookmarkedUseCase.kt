package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Spec 013 R9 — drives the BrowserScreen star-icon fill state. Returns `true`
 * iff at least one bookmark exists with the given URL (regardless of folder).
 */
class IsUrlBookmarkedUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(url: String): Flow<Boolean> =
        repository.observeBookmarksByUrl(url).map { it.isNotEmpty() }
}
