package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Spec 015 FR-021 / FR-024a – FR-024c — pair each record with its live status.
 *
 * **This is where the hybrid source of truth is joined on the read path**, and it lives in
 * a use case rather than inside a repository on purpose: the obvious alternative would make
 * `DownloadsRepositoryImpl` depend on an Android system service and blur the layering
 * Constitution §IV mandates. Keeping the join here means that rule holds with no exception
 * to document.
 *
 * ### The FR-024a fallback
 *
 * The platform prunes its own records on its own schedule, so a handle can stop being
 * recognised at any time — including for a transfer that never finished. The design does
 * not model *when* that happens, only what to do about it:
 *
 * ```
 * platform recognises the handle → use what it reports
 * platform does not             → recorded file present ? Complete : Missing
 * ```
 *
 * The fallback can never yield an in-flight state: a transfer the platform has forgotten is
 * not running, whatever the last thing we saw said. Nothing is written to storage either
 * (FR-024c) — the resolution is recomputed on every read.
 *
 * Known limitation, accepted during clarification (A15): a download that failed long ago
 * and was then pruned is indistinguishable from one that succeeded and whose file the user
 * later deleted. Both resolve to [DownloadStatus.Missing], and the remedy offered to the
 * user is the same either way.
 */
class ResolveDownloadStatusUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
) {

    /**
     * Resolve a whole list in **one** platform round trip rather than one per row
     * (FR-021, SC-006). File-presence checks only run for the handles the platform has
     * actually forgotten, which in the common case is none of them.
     */
    suspend operator fun invoke(records: List<DownloadRecord>): List<DownloadListItem> {
        if (records.isEmpty()) return emptyList()

        val reported = gateway.queryStatuses(records.map { it.transferHandle })
        return records.map { record ->
            val status = reported[record.transferHandle] ?: resolveForgotten(record)
            DownloadListItem(record = backfillCompletionMetadata(record, status), status = status)
        }
    }

    /** Resolve a single record. Convenience for callers acting on one row. */
    suspend operator fun invoke(record: DownloadRecord): DownloadListItem =
        DownloadListItem(
            record = record,
            status = gateway.queryStatus(record.transferHandle) ?: resolveForgotten(record),
        )

    /**
     * FR-014 — record where the finished file landed **and what it ended up called**, the
     * first time we see it complete.
     *
     * The platform only knows both once the transfer succeeds, so there is no earlier moment
     * to capture them. Without this the URI column stayed null forever, and the FR-024a
     * fallback had nothing to work with.
     *
     * The resolved name matters just as much as the URI, because the platform renames
     * colliding downloads itself (FR-007). Three downloads of `dup.txt` become `dup.txt`,
     * `dup-1.txt` and `dup-2.txt` on disk while all three records still say `dup.txt` — so
     * the list shows the same name three times, and, now that presence is answered by
     * looking for the real file, all three rows probe for whichever one happens to exist.
     *
     * This writes **durable metadata** — where the file is and what it is named — not live
     * transfer state, so FR-015 and FR-024c are untouched. It runs at most once per
     * download: the moment the URI is stored, the null check stops matching.
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
        // The platform no longer knows this transfer, so it cannot tell us a size either;
        // null is the honest answer rather than a fabricated one.
        if (gateway.fileExists(record.localUri, record.fileName)) {
            DownloadStatus.Complete()
        } else {
            DownloadStatus.Missing
        }
}
