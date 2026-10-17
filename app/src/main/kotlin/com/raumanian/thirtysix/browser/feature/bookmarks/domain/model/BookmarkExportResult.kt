package com.raumanian.thirtysix.browser.feature.bookmarks.domain.model

sealed interface BookmarkExportResult {
    data class Exported(val bookmarks: Int, val folders: Int) : BookmarkExportResult

    data object NothingToExport : BookmarkExportResult

    data object WriteFailed : BookmarkExportResult
}
