package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Spec 015 FR-036 – FR-039 — stop an in-flight transfer.
 *
 * Cancelling asks the platform to remove the transfer, which both stops it and discards the
 * partially written file (FR-037) — the app never deletes the partial itself, because on
 * API 29+ it may hold no write access to a file it did not place there.
 *
 * **The row is removed as well**, and that is a deliberate reading of FR-038 rather than an
 * oversight. Once the platform drops the transfer, its handle stops being recognised and the
 * FR-024a fallback would resolve the row to `Missing` — presenting a download the user
 * deliberately cancelled as a file that mysteriously vanished. US6's acceptance scenario
 * allows the entry to be "either gone or clearly marked as cancelled"; gone is the honest
 * option here, because the platform gives us nothing to distinguish the two afterwards.
 *
 * A cancel that arrives after the transfer already finished leaves the file intact and
 * reports no cancellation (FR-039): the status is re-read first, and a terminal entry is
 * refused outright.
 */
class CancelDownloadUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
) {

    /** @return true when a live transfer was actually cancelled. */
    suspend operator fun invoke(item: DownloadListItem): Boolean {
        // FR-039 — re-check against the platform rather than the snapshot the UI drew from,
        // which may be up to one poll interval stale.
        val current = gateway.queryStatus(item.record.transferHandle)
        val stillCancellable = current != null && current.isInFlight

        return when {
            !stillCancellable -> false
            !gateway.cancel(item.record.transferHandle) -> false
            else -> {
                repository.deleteById(item.record.id)
                true
            }
        }
    }
}
