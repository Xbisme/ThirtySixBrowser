package com.raumanian.thirtysix.browser.presentation.downloads

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter
import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.data.local.download.ExternalFileOpener
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.domain.usecase.CancelDownloadUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteDownloadedFileUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveDownloadsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.OpenDownloadedFileUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RemoveDownloadRecordUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ResolveDownloadStatusUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Spec 015 — shared doubles so the Compose tests can drive [DownloadsViewModel] directly.
 *
 * No Hilt and no WebView, mirroring `HistoryScreenTestDoubles` from Spec 014: the project
 * documents that setup as flaky, and none of these tests need a real graph to assert what
 * the screen renders.
 */

internal fun instrumentedRecord(
    id: Long,
    fileName: String,
    handle: Long = id * 10,
    localUri: String? = "content://downloads/$id",
    createdAt: Long = 1_700_000_000_000L - id * 1_000L,
) = DownloadRecord(
    id = id,
    sourceUrl = "https://example.com/$fileName",
    fileName = fileName,
    mimeType = "application/pdf",
    createdAt = createdAt,
    transferHandle = handle,
    localUri = localUri,
)

/**
 * Builds a view model over fixed records whose statuses the test dictates outright, so a
 * row's rendered state is a property of the test rather than of any real transfer.
 */
internal fun instrumentedViewModel(
    records: List<DownloadRecord>,
    statuses: Map<Long, DownloadStatus>,
    existingFiles: Set<String> = emptySet(),
): DownloadsViewModel {
    val repository = InstrumentedDownloadsRepository(records)
    val gateway = InstrumentedGateway(statuses, existingFiles)
    return DownloadsViewModel(
        observeDownloads = ObserveDownloadsUseCase(repository),
        resolveStatus = ResolveDownloadStatusUseCase(gateway, repository),
        openDownloadedFile = OpenDownloadedFileUseCase(gateway, InstrumentedNoopOpener),
        removeDownloadRecord = RemoveDownloadRecordUseCase(repository),
        deleteDownloadedFile = DeleteDownloadedFileUseCase(gateway, repository),
        cancelDownload = CancelDownloadUseCase(gateway, repository),
        clipboardWriter = InstrumentedNoopClipboard,
        dispatchers = InstrumentedDispatcherProvider,
    )
}

internal class InstrumentedDownloadsRepository(initial: List<DownloadRecord>) : DownloadsRepository {
    private val records = MutableStateFlow(initial)
    val current: List<DownloadRecord> get() = records.value

    override suspend fun insert(record: DownloadRecord): Long = 0L
    override fun observeAll(): Flow<List<DownloadRecord>> = records
    override suspend fun getById(id: Long): DownloadRecord? = records.value.firstOrNull { it.id == id }
    override suspend fun updateLocalUri(id: Long, localUri: String) = Unit

    override suspend fun deleteById(id: Long): Boolean {
        val before = records.value.size
        records.value = records.value.filterNot { it.id == id }
        return records.value.size < before
    }

    override suspend fun count(): Int = records.value.size
}

internal class InstrumentedGateway(
    private val statuses: Map<Long, DownloadStatus>,
    private val existingFiles: Set<String>,
) : DownloadManagerGateway {
    val cancelled = mutableListOf<Long>()

    override suspend fun enqueue(request: DownloadRequest): Long? = null
    override suspend fun queryStatus(transferHandle: Long): DownloadStatus? = statuses[transferHandle]
    override suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus> =
        statuses.filterKeys { it in transferHandles }

    override suspend fun cancel(transferHandle: Long): Boolean {
        cancelled += transferHandle
        return true
    }

    override suspend fun contentUriFor(transferHandle: Long): String? = null
    override suspend fun fileExists(localUri: String): Boolean = localUri in existingFiles
    override suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean = true
    override suspend fun isAvailable(): Boolean = true
}

private object InstrumentedNoopOpener : ExternalFileOpener {
    override fun open(contentUri: String, mimeType: String): Boolean = true
}

private object InstrumentedNoopClipboard : ClipboardWriter {
    override fun copyUrl(url: String, label: String): Boolean = true
}

private object InstrumentedDispatcherProvider : DispatcherProvider {
    override val main = Dispatchers.Main
    override val io = Dispatchers.Main
    override val default = Dispatchers.Main
    override val unconfined = Dispatchers.Unconfined
}
