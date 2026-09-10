package com.raumanian.thirtysix.browser.presentation.browser

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.domain.usecase.SanitizeDownloadFileNameUseCase
import com.raumanian.thirtysix.browser.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Spec 015 — inert [StartDownloadUseCase] for the three pre-existing instrumented
 * `BrowserScreen` tests, which gained a constructor parameter when downloads landed.
 *
 * The gateway reports the platform as unavailable, so the use case short-circuits before
 * reaching the repository: no real download can be started from a UI test by accident.
 */
internal fun instrumentedNoOpStartDownloadUseCase(): StartDownloadUseCase = StartDownloadUseCase(
    gateway = InstrumentedInertGateway,
    repository = InstrumentedInertDownloadsRepository,
    sanitizeFileName = SanitizeDownloadFileNameUseCase(),
)

private object InstrumentedInertGateway : DownloadManagerGateway {
    override suspend fun enqueue(request: DownloadRequest): Long? = null
    override suspend fun queryStatus(transferHandle: Long): DownloadStatus? = null
    override suspend fun queryStatuses(transferHandles: List<Long>) = emptyMap<Long, DownloadStatus>()
    override suspend fun cancel(transferHandle: Long) = false
    override suspend fun contentUriFor(transferHandle: Long): String? = null
    override suspend fun fileExists(localUri: String) = false
    override suspend fun deleteFile(transferHandle: Long, fileName: String) = false
    override suspend fun isAvailable() = false
}

private object InstrumentedInertDownloadsRepository : DownloadsRepository {
    override suspend fun insert(record: DownloadRecord): Long =
        error("unreachable: the inert gateway rejects every enqueue before insert is called")

    override fun observeAll(): Flow<List<DownloadRecord>> = flowOf(emptyList())
    override suspend fun getById(id: Long): DownloadRecord? = null
    override suspend fun updateLocalUri(id: Long, localUri: String) = Unit
    override suspend fun deleteById(id: Long) = false
    override suspend fun count() = 0
}
