package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject

/** Spec 015 — outcome of asking the platform to start a download. */
sealed class StartDownloadResult {

    /** The platform accepted the transfer and a record was written. */
    data class Started(val fileName: String) : StartDownloadResult()

    /**
     * The platform download service is unavailable — disabled by the user, absent, or
     * refusing. **No record was created** (FR-008b), and the caller MUST surface a
     * localized message rather than failing silently (FR-008a).
     */
    data object ServiceUnavailable : StartDownloadResult()
}

/**
 * Spec 015 FR-001 – FR-008b — hand a download to the platform and record it.
 *
 * This is the one place the two halves of the hybrid source of truth meet on the write
 * path: the gateway accepts the transfer, and only then does the repository persist a
 * record. Joining them here rather than inside the repository is what keeps a repository
 * free of any platform dependency (Constitution §IV).
 *
 * **Order is the requirement, not an implementation detail.** Enqueue first, persist
 * second. A record written before the platform accepted would carry no handle, and the
 * FR-024a status inference — "handle unknown means the platform forgot it" — would then be
 * unable to tell a forgotten download from one that never started. Every row in the table
 * is therefore backed by a real transfer (FR-008b).
 *
 * **Incognito is not consulted** (FR-014a). Downloads from incognito tabs are recorded
 * identically to any other; there is deliberately no branch, flag, or parameter for it.
 */
class StartDownloadUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
    private val sanitizeFileName: SanitizeDownloadFileNameUseCase,
) {

    suspend operator fun invoke(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        userAgent: String,
        referer: String? = null,
    ): StartDownloadResult {
        val fileName = sanitizeFileName(contentDisposition, url)
        val resolvedMimeType = mimeType.orEmpty()

        val request = DownloadRequest(
            sourceUrl = url,
            fileName = fileName,
            mimeType = resolvedMimeType,
            userAgent = userAgent,
            referer = referer,
        )

        val handle = gateway.enqueue(request) ?: return StartDownloadResult.ServiceUnavailable

        repository.insert(
            DownloadRecord(
                id = 0L,
                sourceUrl = url,
                fileName = fileName,
                mimeType = resolvedMimeType,
                createdAt = System.currentTimeMillis(),
                transferHandle = handle,
                localUri = null,
            ),
        )
        return StartDownloadResult.Started(fileName)
    }
}
