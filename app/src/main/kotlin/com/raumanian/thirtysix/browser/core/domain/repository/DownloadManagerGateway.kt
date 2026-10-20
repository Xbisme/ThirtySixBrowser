package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.DownloadOrder
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus

/**
 * Seam over the system download service. Every method is total: a missing, disabled or throwing
 * service never crashes the browser, and failure shows in the return value.
 */
interface DownloadManagerGateway {

    /**
     * The platform shows its own notifications. Null when the service is unavailable,
     * in which case no record is created.
     */
    suspend fun enqueue(request: DownloadOrder): Long?

    /** Null when the service has pruned the handle: an ordinary outcome, resolved by file presence. */
    suspend fun queryStatus(transferHandle: Long): DownloadStatus? =
        queryStatuses(listOf(transferHandle))[transferHandle]

    /** One query for many rows; handles the service no longer knows are absent. */
    suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus>

    /** Discards a partial file; a completed transfer keeps its file and is not reported cancelled. */
    suspend fun cancel(transferHandle: Long): Boolean

    /** Always a content URI: exposing a `file://` URI to another app throws on every supported API. */
    suspend fun contentUriFor(transferHandle: Long): String?

    /** Takes [fileName] too: the recorded URI dies with the provider's data, when presence matters most. */
    suspend fun fileExists(localUri: String?, fileName: String): Boolean

    /** The name on disk: the platform de-duplicates collisions (`dup.txt` → `dup-1.txt`). */
    suspend fun resolvedFileNameFor(transferHandle: Long): String?

    /**
     * Deletes through the platform's own `remove`, since a content URI only addresses the
     * provider's row. True only once the file is verified gone.
     */
    suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean
}
