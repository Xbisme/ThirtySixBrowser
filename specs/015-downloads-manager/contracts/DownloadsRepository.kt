// CONTRACT — Spec 015 downloads-manager
// This is the documented interface contract used by the planner. The implementation files
// will live at app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/DownloadsRepository.kt
// (interface) + .../data/repository/DownloadsRepositoryImpl.kt (impl), bound by Hilt in
// app/.../di/DownloadsModule.kt.

package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import kotlinx.coroutines.flow.Flow

/**
 * Downloads repository — Spec 015.
 *
 * Owns the **persisted half** of the hybrid source-of-truth decision: identity and durable
 * metadata for every download the platform service accepted. It deliberately knows nothing
 * about live transfer state — no progress, no byte counts, no running/paused flag (FR-015).
 * Live status comes from [DownloadManagerGateway] and is joined at the use-case layer, so
 * this interface stays a plain Room-backed store with no platform dependency.
 *
 * Constitution §IV: ViewModels MUST go through this interface and never touch the DAO.
 * The repository depends only on its DAO and a dispatcher — never on another repository.
 *
 * **Record-existence invariant (FR-008b)**: a row exists only for a transfer the platform
 * service accepted and returned a handle for. Nothing is inserted before that. This is what
 * makes the FR-024a status inference sound: a record with no handle cannot exist, so a
 * handle the service does not recognise always means *forgotten*, never *never-started*.
 *
 * **No incognito concept (FR-014a)**: downloads started from incognito tabs are recorded
 * identically to any other. There is deliberately no flag, column, or parameter for it.
 */
interface DownloadsRepository {

    /**
     * Persist a record for a transfer the platform service has already accepted.
     *
     * MUST be called only after a handle has been obtained (FR-008b). [record]'s
     * `fileName` MUST already be sanitised (FR-005) — this layer does not re-validate.
     *
     * @return the row id of the inserted record.
     */
    suspend fun insert(record: DownloadRecord): Long

    /**
     * Observe every record, most recent first by creation time (FR-019).
     *
     * Re-emits on insert, update and delete so the list reflects downloads started
     * elsewhere in the app while the screen is open (FR-023). Emits an empty list when
     * there are no records, which the screen renders as the empty state (FR-040).
     */
    fun observeAll(): Flow<List<DownloadRecord>>

    /** Read a single record by its own id, or null when it no longer exists. */
    suspend fun getById(id: Long): DownloadRecord?

    /**
     * Record where the finished file landed, once the transfer completes.
     *
     * Writing the content URI is the only post-insert mutation in this feature; it is
     * durable metadata (where the file is), not live transfer state, so it does not
     * violate FR-015.
     */
    suspend fun updateLocalUri(id: Long, localUri: String)

    /**
     * Delete one record, leaving the downloaded file untouched (FR-031).
     *
     * The caller is responsible for enforcing FR-034a — removal MUST be offered only for
     * entries in a terminal state, so that no in-flight transfer is orphaned (FR-034b).
     * This method does not itself cancel anything.
     *
     * @return true when a row was removed, false when the id was already gone.
     */
    suspend fun deleteById(id: Long): Boolean

    /** Total number of records. Backs the empty-state decision without loading the list. */
    suspend fun count(): Int
}
