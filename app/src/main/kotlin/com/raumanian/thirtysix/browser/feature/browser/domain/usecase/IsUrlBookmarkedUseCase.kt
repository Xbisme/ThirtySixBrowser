package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether any bookmark, in any folder, has this URL; drives the star icon. */
class IsUrlBookmarkedUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(url: String): Flow<Boolean> =
        repository.observeBookmarksByUrl(url).map { it.isNotEmpty() }
}
