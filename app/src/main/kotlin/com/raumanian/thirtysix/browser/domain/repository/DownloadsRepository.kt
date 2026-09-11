package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import kotlinx.coroutines.flow.Flow

/**
 * Spec 015 — the **persisted half** of the hybrid source-of-truth decision.
 *
 * Owns identity and durable metadata for every download the platform accepted. It
 * deliberately knows nothing about live transfer state — no progress, no byte counts, no
 * running/paused flag (FR-015). Live status comes from
 * [com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway] and the two
 * are joined at the **use-case** layer.
 *
 * That split is load-bearing, not stylistic. The obvious alternative — having the
 * implementation call the platform itself to fill in status — would make a repository
 * depend on an Android system service and blur the layering Constitution §IV mandates.
 * Keeping this interface Room-only means the rule holds with no exception to document.
 *
 * **Record-existence invariant (FR-008b)**: a row exists only for a transfer the platform
 * accepted and returned a handle for. Nothing is inserted before that. This is what makes
 * the FR-024a status inference sound: a record with no handle cannot exist, so a handle
 * the platform does not recognise always means *forgotten*, never *never-started*.
 *
 * **No incognito concept (FR-014a)**: downloads started from incognito tabs are recorded
 * identically. There is deliberately no flag, column, or parameter for it.
 */
interface DownloadsRepository {

    /**
     * Persist a record for a transfer the platform has already accepted.
     *
     * MUST be called only after a handle has been obtained (FR-008b). The record's
     * `fileName` MUST already be sanitised (FR-005) — this layer does not re-validate.
     *
     * @return the row id of the inserted record.
     */
    suspend fun insert(record: DownloadRecord): Long

    /**
     * Observe every record, most recent first (FR-019).
     *
     * Re-emits on insert, update and delete, so the list reflects downloads started
     * elsewhere in the app while the screen is open (FR-023). Emits an empty list when
     * there are none, which the screen renders as the empty state (FR-040).
     */
    fun observeAll(): Flow<List<DownloadRecord>>

    /** Read one record by its own id, or null when it no longer exists. */
    suspend fun getById(id: Long): DownloadRecord?

    /**
     * Record where the finished file landed and what it is actually called, once the
     * transfer completes (FR-014).
     *
     * The only post-insert mutation in this feature. It stores durable metadata — *where*
     * the file is and *what* it is named — not live transfer state, so FR-015 is not
     * violated. The name can differ from the one requested because the platform resolves
     * filename collisions itself (FR-007).
     */
    suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String)

    /**
     * Delete one record, leaving the downloaded file untouched (FR-031).
     *
     * The caller enforces FR-034a — removal is offered only for entries in a terminal
     * state, so no in-flight transfer is orphaned (FR-034b). This method cancels nothing.
     *
     * @return true when a row was removed, false when the id was already gone.
     */
    suspend fun deleteById(id: Long): Boolean

    /** Total number of records. Backs the empty-state decision without loading the list. */
    suspend fun count(): Int
}
