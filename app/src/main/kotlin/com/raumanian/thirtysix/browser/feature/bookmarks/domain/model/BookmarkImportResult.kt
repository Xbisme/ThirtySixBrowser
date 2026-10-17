package com.raumanian.thirtysix.browser.feature.bookmarks.domain.model

sealed interface BookmarkImportResult {
    data class Imported(
        val folderId: Long,
        val folderName: String,
        val imported: Int,
        val duplicatesSkipped: Int,
        val unsupportedSkipped: Int,
    ) : BookmarkImportResult

    data object NotABookmarkFile : BookmarkImportResult

    data object NothingToImport : BookmarkImportResult

    /** The file is over the size cap, or importing it would pass the app's bookmark or folder limit. */
    data object TooLarge : BookmarkImportResult

    data object ReadFailed : BookmarkImportResult

    data object WriteFailed : BookmarkImportResult
}
