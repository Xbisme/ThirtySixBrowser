package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkUrlValidationError
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.BookmarkUrlValidator
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/**
 * Adds a bookmark. The creation time doubles as `sortOrder`, so new bookmarks sort first.
 * A blank [title] falls back to the URL; a null `parentFolderId` means the root.
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
