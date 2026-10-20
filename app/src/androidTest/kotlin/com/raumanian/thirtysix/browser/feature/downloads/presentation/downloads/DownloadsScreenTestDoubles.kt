package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import com.raumanian.thirtysix.browser.core.domain.model.DownloadOrder
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ExternalFileOpener
import com.raumanian.thirtysix.browser.core.domain.usecase.ResolveDownloadStatusUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.CancelDownloadUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.DeleteDownloadedFileUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.OpenDownloadedFileUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.RemoveDownloadRecordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// The tests drive [DownloadsViewModel] directly: no Hilt and no WebView, which are flaky to set up.

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

// Statuses are dictated by the test, so a row's rendered state never depends on a real transfer.
internal fun instrumentedViewModel(
    records: List<DownloadRecord>,
    statuses: Map<Long, DownloadStatus>,
    existingFiles: Set<String> = emptySet(),
): DownloadsViewModel {
    val repository = InstrumentedDownloadsRepository(records)
    val gateway = InstrumentedGateway(statuses, existingFiles)
    return DownloadsViewModel(
        downloads = repository,
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
    override suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String) = Unit

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

    override suspend fun enqueue(request: DownloadOrder): Long? = null
    override suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus> =
        statuses.filterKeys { it in transferHandles }

    override suspend fun cancel(transferHandle: Long): Boolean {
        cancelled += transferHandle
        return true
    }

    override suspend fun contentUriFor(transferHandle: Long): String? = null
    override suspend fun resolvedFileNameFor(transferHandle: Long): String? = null

    /** Either a name or a recorded URI counts as present, mirroring production. */
    override suspend fun fileExists(localUri: String?, fileName: String): Boolean =
        fileName in existingFiles || localUri in existingFiles
    override suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean = true
    override suspend fun isAvailable(): Boolean = true
}

private object InstrumentedNoopOpener : ExternalFileOpener {
    override fun open(contentUri: String, mimeType: String, fileName: String): Boolean = true
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
