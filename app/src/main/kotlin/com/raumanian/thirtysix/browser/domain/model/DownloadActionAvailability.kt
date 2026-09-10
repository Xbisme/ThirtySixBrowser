package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 015 FR-030 / FR-034 / FR-034a — which action-sheet entries apply to an entry.
 *
 * A single pure function rather than logic inside the sheet composable, for two reasons:
 * it is unit-testable without a device, and it cannot drift away from the matrix its tests
 * assert. FR-034a states the rules as a table, so this is that table and nothing else.
 */
data class DownloadActionAvailability(
    val canOpen: Boolean,
    val canCopyLink: Boolean,
    val canRemoveFromList: Boolean,
    val canDeleteFile: Boolean,
) {
    companion object {

        /**
         * Evaluate the FR-034a matrix.
         *
         * @param fileIsPresent whether the entry's recorded file is on disk. Callers pass
         *   the same answer the status resolution already computed rather than probing the
         *   filesystem twice.
         */
        fun forStatus(status: DownloadStatus, fileIsPresent: Boolean): DownloadActionAvailability =
            DownloadActionAvailability(
                // Open only when it finished AND the file is actually there.
                canOpen = status is DownloadStatus.Complete && fileIsPresent,
                // The source address is recorded at insert time and never goes away.
                canCopyLink = true,
                // FR-034b — withheld while in flight. Removing the row would discard the
                // handle, leaving a transfer running that the app could neither cancel nor
                // record on completion. Cancel first (FR-036), then remove.
                canRemoveFromList = status.isTerminal,
                // Nothing to delete unless a file exists.
                canDeleteFile = fileIsPresent,
            )
    }
}
