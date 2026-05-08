package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.error.BookmarkUrlValidationError
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.domain.validator.BookmarkUrlValidator
import javax.inject.Inject

/**
 * Spec 013 — adds a new bookmark. Validates URL via [BookmarkUrlValidator]
 * (R5), enforces title and URL length caps from [BrowserLimits], generates
 * `createdAt = System.currentTimeMillis()` and uses it as `sortOrder` (R-spec
 * "recent-first" ordering).
 *
 * @param url raw user input — auto-prepend / trim handled by validator.
 * @param title raw user input — non-blank requirement; falls back to URL on blank.
 * @param parentFolderId target folder, null = root.
 */
class AddBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {

    @Suppress("ReturnCount") // Validation guard branches.
    suspend operator fun invoke(
        url: String,
        title: String,
        parentFolderId: Long?,
    ): Result<Long> {
        val normalizedUrl = when (val validation = BookmarkUrlValidator.validate(url)) {
            is Result.Success -> validation.data
            is Result.Error -> return validation
        }

        if (normalizedUrl.length > BrowserLimits.MAX_BOOKMARK_URL_LENGTH) {
            return Result.Error(
                BookmarkException.UrlValidation(BookmarkUrlValidationError.InvalidScheme),
            )
        }

        val resolvedTitle = title.trim().ifBlank { normalizedUrl }
            .take(BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH)

        val now = System.currentTimeMillis()
        return repository.addBookmark(
            Bookmark(
                id = 0L,
                title = resolvedTitle,
                url = normalizedUrl,
                parentFolderId = parentFolderId,
                createdAt = now,
                sortOrder = now,
            ),
        )
    }
}
