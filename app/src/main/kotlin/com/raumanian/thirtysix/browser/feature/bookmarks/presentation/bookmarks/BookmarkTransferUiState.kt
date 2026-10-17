package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult

data class BookmarkTransferUiState(
    val isSheetOpen: Boolean = false,
    val progress: BookmarkTransferProgress = BookmarkTransferProgress.Idle,
) {
    val isBusy: Boolean
        get() = progress == BookmarkTransferProgress.Importing || progress == BookmarkTransferProgress.Exporting
}

sealed interface BookmarkTransferProgress {
    data object Idle : BookmarkTransferProgress

    data object Importing : BookmarkTransferProgress

    data object Exporting : BookmarkTransferProgress

    /** Kept until dismissed: the summary offers to open the new folder. */
    data class Imported(val result: BookmarkImportResult.Imported) : BookmarkTransferProgress
}
