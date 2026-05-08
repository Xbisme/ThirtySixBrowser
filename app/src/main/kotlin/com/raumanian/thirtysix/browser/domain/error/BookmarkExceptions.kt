package com.raumanian.thirtysix.browser.domain.error

/**
 * Spec 013 — typed exceptions surfaced by [com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository]
 * inside `Result.Error(throwable)`. Use cases / view models map these to
 * `BookmarksErrorEvent` for UI presentation.
 */
sealed class BookmarkException(message: String) : Exception(message) {
    data class BookmarkNotFound(val id: Long) : BookmarkException("Bookmark id=$id not found")

    data class FolderNotFound(val id: Long) : BookmarkException("Folder id=$id not found")

    /**
     * Spec 013 R6 — attempt to move a folder into itself or one of its descendants.
     *
     * @property folderId the folder being moved.
     * @property attemptedParentId the requested new parent (null = root).
     */
    data class FolderCycle(
        val folderId: Long,
        val attemptedParentId: Long?,
    ) : BookmarkException(
        "Cannot move folder $folderId into $attemptedParentId — would create a cycle",
    )

    data class UrlValidation(val reason: BookmarkUrlValidationError) :
        BookmarkException("URL validation failed: $reason")
}

/**
 * Spec 013 R5 — sealed variants for URL validation failures. Use cases /
 * view models map each variant to a localized inline error per FR-006.
 */
sealed class BookmarkUrlValidationError {
    data object Empty : BookmarkUrlValidationError()
    data object InvalidScheme : BookmarkUrlValidationError()
    data object MissingHost : BookmarkUrlValidationError()
}
