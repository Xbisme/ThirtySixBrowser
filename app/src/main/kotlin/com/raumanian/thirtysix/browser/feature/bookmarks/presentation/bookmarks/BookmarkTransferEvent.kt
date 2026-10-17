package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkExportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult

sealed interface BookmarkTransferEvent {
    data object LaunchImportPicker : BookmarkTransferEvent

    data class LaunchExportPicker(val suggestedFileName: String) : BookmarkTransferEvent

    data class OpenFolder(val folderId: Long) : BookmarkTransferEvent

    /** An import that added nothing; shown as a message. */
    data class ImportFailed(val result: BookmarkImportResult) : BookmarkTransferEvent

    data class ExportFinished(val result: BookmarkExportResult) : BookmarkTransferEvent
}
