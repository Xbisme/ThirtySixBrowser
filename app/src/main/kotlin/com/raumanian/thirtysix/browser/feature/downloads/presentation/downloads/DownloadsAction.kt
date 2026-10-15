package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord

sealed interface DownloadsAction {
    data class ItemClicked(val item: DownloadListItem) : DownloadsAction

    data class ItemLongPressed(val item: DownloadListItem) : DownloadsAction

    data object ActionSheetDismissed : DownloadsAction

    data class CopyLinkClicked(val item: DownloadListItem) : DownloadsAction

    data class RemoveFromListClicked(val item: DownloadListItem) : DownloadsAction

    data class DeleteFileRequested(val record: DownloadRecord) : DownloadsAction

    data class DeleteFileConfirmed(val record: DownloadRecord) : DownloadsAction

    data object DeleteConfirmationDismissed : DownloadsAction

    data class CancelClicked(val item: DownloadListItem) : DownloadsAction
}
