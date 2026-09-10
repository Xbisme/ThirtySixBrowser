package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.data.local.download.ExternalFileOpener
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Spec 015 — shared doubles for the download use-case tests. */

internal fun testRecord(
    id: Long = 1L,
    handle: Long = 10L,
    fileName: String = "a.pdf",
    mimeType: String = "application/pdf",
    localUri: String? = "content://downloads/10",
) = DownloadRecord(
    id = id,
    sourceUrl = "https://example.com/$fileName",
    fileName = fileName,
    mimeType = mimeType,
    createdAt = 1_000L,
    transferHandle = handle,
    localUri = localUri,
)

internal fun testItem(
    status: DownloadStatus = DownloadStatus.Complete(),
    record: DownloadRecord = testRecord(),
) = DownloadListItem(record = record, status = status)

/**
 * Configurable gateway that records what it was asked to do, so tests can assert on
 * *absence* of a call as readily as on its result.
 */
internal open class RecordingGateway(
    private val statuses: MutableMap<Long, DownloadStatus> = mutableMapOf(),
    private val existingFiles: MutableSet<String> = mutableSetOf(),
    private val cancelSucceeds: Boolean = true,
    private val deleteSucceeds: Boolean = true,
    private val contentUri: String? = null,
) : DownloadManagerGateway {

    val cancelled = mutableListOf<Long>()
    val deletedFiles = mutableListOf<String>()

    fun setStatus(handle: Long, status: DownloadStatus?) {
        if (status == null) statuses.remove(handle) else statuses[handle] = status
    }

    fun addFile(uri: String) = existingFiles.add(uri)

    override suspend fun enqueue(request: DownloadRequest): Long? = null
    override suspend fun queryStatus(transferHandle: Long): DownloadStatus? = statuses[transferHandle]
    override suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus> =
        statuses.filterKeys { it in transferHandles }

    override suspend fun cancel(transferHandle: Long): Boolean {
        cancelled += transferHandle
        return cancelSucceeds
    }

    override suspend fun contentUriFor(transferHandle: Long): String? = contentUri
    override suspend fun fileExists(localUri: String): Boolean = localUri in existingFiles

    override suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean {
        deletedFiles += fileName
        return deleteSucceeds
    }

    override suspend fun isAvailable(): Boolean = true
}

/** In-memory [DownloadsRepository] backed by a list, so deletes are observable. */
internal class InMemoryDownloadsRepository(
    initial: List<DownloadRecord> = emptyList(),
) : DownloadsRepository {

    private val records = MutableStateFlow(initial)

    val current: List<DownloadRecord> get() = records.value

    override suspend fun insert(record: DownloadRecord): Long {
        val id = (records.value.maxOfOrNull { it.id } ?: 0L) + 1L
        records.value = records.value + record.copy(id = id)
        return id
    }

    override fun observeAll(): Flow<List<DownloadRecord>> = records

    override suspend fun getById(id: Long): DownloadRecord? = records.value.firstOrNull { it.id == id }

    override suspend fun updateLocalUri(id: Long, localUri: String) {
        records.value = records.value.map { if (it.id == id) it.copy(localUri = localUri) else it }
    }

    override suspend fun deleteById(id: Long): Boolean {
        val before = records.value.size
        records.value = records.value.filterNot { it.id == id }
        return records.value.size < before
    }

    override suspend fun count(): Int = records.value.size
}

/** Records what was handed to an external app, and whether anything could handle it. */
internal class RecordingFileOpener(private val succeeds: Boolean = true) : ExternalFileOpener {
    val opened = mutableListOf<Pair<String, String>>()

    override fun open(contentUri: String, mimeType: String): Boolean {
        opened += contentUri to mimeType
        return succeeds
    }
}
