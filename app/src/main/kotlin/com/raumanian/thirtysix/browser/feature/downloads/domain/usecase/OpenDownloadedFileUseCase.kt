package com.raumanian.thirtysix.browser.feature.downloads.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.ExternalFileOpener
import javax.inject.Inject

sealed class OpenDownloadResult {
    data object Opened : OpenDownloadResult()

    data object NotComplete : OpenDownloadResult()

    /** The file is gone from disk; offer to clear the stale row. */
    data object FileMissing : OpenDownloadResult()

    data object NoAppAvailable : OpenDownloadResult()
}

// The file is only ever handed over as a platform content URI, never a filesystem path.
class OpenDownloadedFileUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val opener: ExternalFileOpener,
) {

    suspend operator fun invoke(item: DownloadListItem): OpenDownloadResult = when {
        // Missing is terminal: without this branch it would fall through to NotComplete and tell the
        // user to wait for a transfer that finished long ago.
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

    // The URI is resolved before presence is checked, so a record with none stored is judged on the platform's.
    private suspend fun resolveReadableUri(item: DownloadListItem): String? {
        val uri = item.record.localUri ?: gateway.contentUriFor(item.record.transferHandle)
        return when {
            uri == null -> null
            gateway.fileExists(uri, item.record.fileName) -> uri
            else -> null
        }
    }
}
