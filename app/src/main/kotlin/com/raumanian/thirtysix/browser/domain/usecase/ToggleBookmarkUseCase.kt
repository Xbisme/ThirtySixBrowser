package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Spec 013 R9 / Q4 — drives the star icon "toggle" behaviour on
 * [com.raumanian.thirtysix.browser.presentation.browser.BrowserScreen].
 *
 * If no bookmark exists for [url] → add one at root via [AddBookmarkUseCase].
 * If at least one exists → delete the most-recently-created entry, leaving
 * any manual duplicates untouched.
 *
 * Returns a [ToggleBookmarkResult] indicating which action the UI should
 * surface (snackbar message via [com.raumanian.thirtysix.browser.presentation.browser.BookmarkSnackbarEvent]).
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
