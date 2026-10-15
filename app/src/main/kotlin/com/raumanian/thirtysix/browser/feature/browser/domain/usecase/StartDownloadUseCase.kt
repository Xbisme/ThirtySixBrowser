package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadOrder
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import javax.inject.Inject

/** Outcome of asking the platform to start a download. */
sealed class StartDownloadResult {

    data class Started(val fileName: String) : StartDownloadResult()

    /** The download service is unavailable; no record was created, and the caller must say so. */
    data object ServiceUnavailable : StartDownloadResult()
}

/**
 * Hands a download to the platform, then records it, in that order: a record with no platform
 * handle could not be told apart from one the platform forgot. Incognito is deliberately not
 * consulted; its downloads are recorded like any other.
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

        val request = DownloadOrder(
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
