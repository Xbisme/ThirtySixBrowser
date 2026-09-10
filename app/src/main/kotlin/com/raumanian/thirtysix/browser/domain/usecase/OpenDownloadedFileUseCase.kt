package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.data.local.download.ExternalFileOpener
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import javax.inject.Inject

/** Spec 015 — outcome of tapping a row. */
sealed class OpenDownloadResult {
    /** FR-025 — handed off to an external app. */
    data object Opened : OpenDownloadResult()

    /** FR-028 — the transfer has not finished, so there is nothing to open. */
    data object NotComplete : OpenDownloadResult()

    /** FR-029 — the file is gone from disk; offer to clear the stale row. */
    data object FileMissing : OpenDownloadResult()

    /** FR-027 — nothing on the device handles this type. */
    data object NoAppAvailable : OpenDownloadResult()
}

/**
 * Spec 015 FR-025 – FR-029 — open a completed download.
 *
 * Every failure mode gets its own outcome rather than one generic error, because each one
 * calls for a different message and a different next step: wait, clear the stale row, or
 * install something that can read the file. Collapsing them would leave the user guessing.
 *
 * The file is always handed over as a **content URI** obtained from the platform (FR-026);
 * this use case never sees or constructs a filesystem path.
 */
class OpenDownloadedFileUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val opener: ExternalFileOpener,
) {

    suspend operator fun invoke(item: DownloadListItem): OpenDownloadResult = when {
        // FR-024b — a row already resolved as missing gets FR-029's removal offer, not the
        // "still downloading" message. Ordering matters: Missing is terminal, so without
        // this branch it would fall through to NotComplete and tell the user to wait for a
        // transfer that finished long ago.
        item.status is DownloadStatus.Missing -> OpenDownloadResult.FileMissing
        item.status !is DownloadStatus.Complete -> OpenDownloadResult.NotComplete
        else -> when (val uri = resolveReadableUri(item)) {
            null -> OpenDownloadResult.FileMissing
            else -> if (opener.open(uri, item.record.mimeType)) {
                OpenDownloadResult.Opened
            } else {
                OpenDownloadResult.NoAppAvailable
            }
        }
    }

    /**
     * Prefer the URI recorded at completion; fall back to asking the platform, which still
     * knows one as long as the transfer has not been pruned. Either way the file must
     * actually still be there — a URI pointing at a deleted file is FR-029, not a
     * successful open.
     *
     * The candidate URI is resolved *before* presence is checked, not after, so that a
     * record with nothing stored yet is still judged on the URI the platform can supply
     * rather than being written off as missing.
     */
    private suspend fun resolveReadableUri(item: DownloadListItem): String? {
        val uri = item.record.localUri ?: gateway.contentUriFor(item.record.transferHandle)
        return when {
            uri == null -> null
            gateway.fileExists(uri, item.record.fileName) -> uri
            else -> null
        }
    }
}
