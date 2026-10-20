package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Pairs each record with its live status (here, so the repository never touches the platform).
 * A handle the platform has pruned resolves from the file alone: Complete if present, else Missing.
 * Without a recorded URI it is Missing, since a same-named file may belong to another download.
 */
class ResolveDownloadStatusUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
) {

    /** Resolves the list in one platform query; file checks run only for forgotten handles. */
    suspend operator fun invoke(records: List<DownloadRecord>): List<DownloadListItem> {
        if (records.isEmpty()) return emptyList()

        val reported = gateway.queryStatuses(records.map { it.transferHandle })
        return records.map { record ->
            val status = reported[record.transferHandle] ?: resolveForgotten(record)
            DownloadListItem(record = backfillCompletionMetadata(record, status), status = status)
        }
    }

    suspend operator fun invoke(record: DownloadRecord): DownloadListItem =
        DownloadListItem(
            record = record,
            status = gateway.queryStatus(record.transferHandle) ?: resolveForgotten(record),
        )

    /**
     * Stores the finished file's URI and final name the first time it is seen complete; the
     * platform knows them only then, and renames colliding downloads (`dup.txt` → `dup-1.txt`).
     */
    private suspend fun backfillCompletionMetadata(
        record: DownloadRecord,
        status: DownloadStatus,
    ): DownloadRecord {
        val needsBackfill = status is DownloadStatus.Complete && record.localUri == null
        val uri = if (needsBackfill) gateway.contentUriFor(record.transferHandle) else null
        return when (uri) {
            null -> record
            else -> {
                val fileName = gateway.resolvedFileNameFor(record.transferHandle) ?: record.fileName
                repository.updateCompletionMetadata(record.id, uri, fileName)
                record.copy(localUri = uri, fileName = fileName)
            }
        }
    }

    private suspend fun resolveForgotten(record: DownloadRecord): DownloadStatus =
        if (record.localUri != null && gateway.fileExists(record.localUri, record.fileName)) {
            DownloadStatus.Complete()
        } else {
            DownloadStatus.Missing
        }
}
