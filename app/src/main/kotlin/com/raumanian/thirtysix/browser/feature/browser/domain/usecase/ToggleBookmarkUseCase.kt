package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/**
 * The star icon's toggle: with no bookmark for [url], adds one at root; otherwise deletes the most
 * recently created one, leaving manual duplicates untouched.
 */
class ToggleBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
    private val addBookmark: AddBookmarkUseCase,
) {
    suspend operator fun invoke(
        url: String,
        fallbackTitle: String,
    ): Result<ToggleBookmarkResult> {
        val count = repository.countBookmarksByUrl(url)
        return if (count == 0) {
            when (val add = addBookmark(url = url, title = fallbackTitle, parentFolderId = null)) {
                is Result.Success -> Result.Success(ToggleBookmarkResult.Added)
                is Result.Error -> add
            }
        } else {
            when (val del = repository.deleteMostRecentBookmarkByUrl(url)) {
                is Result.Success -> Result.Success(ToggleBookmarkResult.Removed)
                is Result.Error -> del
            }
        }
    }
}

sealed class ToggleBookmarkResult {
    data object Added : ToggleBookmarkResult()
    data object Removed : ToggleBookmarkResult()
}
