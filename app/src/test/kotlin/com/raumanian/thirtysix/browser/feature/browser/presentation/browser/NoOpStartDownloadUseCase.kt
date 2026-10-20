package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.model.DownloadOrder
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SanitizeDownloadFileNameUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Inert [StartDownloadUseCase]: the gateway reports the platform unavailable, so nothing is ever
 * recorded and a test that triggers a download by accident cannot pass by it.
 */
internal fun noOpStartDownloadUseCase(): StartDownloadUseCase = StartDownloadUseCase(
    gateway = InertDownloadManagerGateway,
    repository = InertDownloadsRepository,
    sanitizeFileName = SanitizeDownloadFileNameUseCase(),
)

private object InertDownloadManagerGateway : DownloadManagerGateway {
    override suspend fun enqueue(request: DownloadOrder): Long? = null
    override suspend fun queryStatuses(transferHandles: List<Long>) = emptyMap<Long, DownloadStatus>()
    override suspend fun cancel(transferHandle: Long) = false
    override suspend fun contentUriFor(transferHandle: Long): String? = null
    override suspend fun fileExists(localUri: String?, fileName: String) = false
    override suspend fun resolvedFileNameFor(transferHandle: Long): String? = null
    override suspend fun deleteFile(transferHandle: Long, fileName: String) = false
}

private object InertDownloadsRepository : DownloadsRepository {
    override suspend fun insert(record: DownloadRecord): Long =
        error("unreachable: the inert gateway rejects every enqueue before insert is called")

    override fun observeAll(): Flow<List<DownloadRecord>> = flowOf(emptyList())
    override suspend fun getById(id: Long): DownloadRecord? = null
    override suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String) = Unit
    override suspend fun deleteById(id: Long) = false
}
