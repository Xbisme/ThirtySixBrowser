package com.raumanian.thirtysix.browser.feature.downloads.domain.model

import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus

// Which action-sheet entries apply to an entry; a pure table so tests can pin it without a device.
data class DownloadActionAvailability(
    val canOpen: Boolean,
    val canCopyLink: Boolean,
    val canRemoveFromList: Boolean,
    val canDeleteFile: Boolean,
) {
    companion object {

        // fileIsPresent is the answer status resolution already computed, so the disk is probed once.
        fun forStatus(status: DownloadStatus, fileIsPresent: Boolean): DownloadActionAvailability =
            DownloadActionAvailability(
                canOpen = status is DownloadStatus.Complete && fileIsPresent,
                canCopyLink = true,
                // Withheld while in flight: removing the row discards the handle, leaving a transfer the
                // app could neither cancel nor record on completion. Cancel first, then remove.
                canRemoveFromList = status.isTerminal,
                canDeleteFile = fileIsPresent,
            )
    }
}
