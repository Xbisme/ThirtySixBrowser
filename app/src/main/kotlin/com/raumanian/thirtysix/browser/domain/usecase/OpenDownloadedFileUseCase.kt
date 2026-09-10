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
     * knows it as long as the transfer has not been pruned. Either way the file must
     * actually be readable — a recorded URI pointing at a deleted file is FR-029, not a
     * successful open.
     */
    private suspend fun resolveReadableUri(item: DownloadListItem): String? =
        item.record.localUri?.takeIf { gateway.fileExists(it) }
            ?: gateway.contentUriFor(item.record.transferHandle)?.takeIf { gateway.fileExists(it) }
}
