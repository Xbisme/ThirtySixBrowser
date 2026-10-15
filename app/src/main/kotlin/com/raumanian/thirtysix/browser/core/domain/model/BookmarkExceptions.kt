package com.raumanian.thirtysix.browser.core.domain.model

sealed class BookmarkException(message: String) : Exception(message) {
    data class BookmarkNotFound(val id: Long) : BookmarkException("Bookmark id=$id not found")

    data class FolderNotFound(val id: Long) : BookmarkException("Folder id=$id not found")

    /** Moving a folder into itself or a descendant. A null [attemptedParentId] is the root. */
    data class FolderCycle(
        val folderId: Long,
        val attemptedParentId: Long?,
    ) : BookmarkException(
        "Cannot move folder $folderId into $attemptedParentId — would create a cycle",
    )

    data class UrlValidation(val reason: BookmarkUrlValidationError) :
        BookmarkException("URL validation failed: $reason")
}

sealed class BookmarkUrlValidationError {
    data object Empty : BookmarkUrlValidationError()
    data object InvalidScheme : BookmarkUrlValidationError()
    data object MissingHost : BookmarkUrlValidationError()
}
