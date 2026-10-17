package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportNames

sealed interface BookmarkTransferAction {
    data object SheetOpen : BookmarkTransferAction

    data object SheetDismiss : BookmarkTransferAction

    data object ImportClick : BookmarkTransferAction

    data class ExportClick(val suggestedFileName: String) : BookmarkTransferAction

    data class ImportFilePicked(val uri: String, val names: BookmarkImportNames) : BookmarkTransferAction

    data class ExportFilePicked(val uri: String) : BookmarkTransferAction

    data object SummaryDismiss : BookmarkTransferAction

    data class OpenImportedFolder(val folderId: Long) : BookmarkTransferAction
}
