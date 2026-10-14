package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.BookmarkUrlValidator
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/**
 * Spec 013 FR-021 / FR-022 — edits a bookmark's title, URL, and parent
 * folder. Validates URL via [BookmarkUrlValidator]; title falls back to URL
 * on blank.
 */
class UpdateBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    @Suppress("ReturnCount") // Three returns model the three guard branches (missing / invalid url / success).
    suspend operator fun invoke(
        bookmarkId: Long,
        title: String,
        url: String,
        parentFolderId: Long?,
    ): Result<Unit> {
        val existing = repository.getBookmark(bookmarkId)
            ?: return Result.Error(BookmarkException.BookmarkNotFound(bookmarkId))

        val normalizedUrl = when (val v = BookmarkUrlValidator.validate(url)) {
            is Result.Success -> v.data
            is Result.Error -> return v
        }

        val resolvedTitle = title.trim().ifBlank { normalizedUrl }
            .take(BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH)

        return repository.updateBookmark(
            existing.copy(
                title = resolvedTitle,
                url = normalizedUrl,
                parentFolderId = parentFolderId,
            ),
        )
    }
}
