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

    /** The download service fetches only http(s); `blob:` and `data:` live inside the page. */
    data object Unsupported : StartDownloadResult()
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

    @Suppress("LongParameterList")
    suspend operator fun invoke(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        userAgent: String,
        referer: String? = null,
        cookies: String? = null,
    ): StartDownloadResult {
        if (!url.isFetchable()) return StartDownloadResult.Unsupported
        val order = DownloadOrder(
            sourceUrl = url,
            fileName = sanitizeFileName(contentDisposition, url),
            mimeType = mimeType.orEmpty(),
            userAgent = userAgent,
            referer = referer,
            cookies = cookies,
        )
        return enqueueAndRecord(order)
    }

    private suspend fun enqueueAndRecord(order: DownloadOrder): StartDownloadResult {
        val handle = gateway.enqueue(order) ?: return StartDownloadResult.ServiceUnavailable
        repository.insert(
            DownloadRecord(
                id = 0L,
                sourceUrl = order.sourceUrl,
                fileName = order.fileName,
                mimeType = order.mimeType,
                createdAt = System.currentTimeMillis(),
                transferHandle = handle,
                localUri = null,
            ),
        )
        return StartDownloadResult.Started(order.fileName)
    }

    private fun String.isFetchable(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}
