package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Spec 014 — single source of truth for the persisted browsing-history slice.
 *
 * ViewModels MUST go through this interface (Constitution §IV) and never touch HistoryDao
 * directly. Recording is gated by the caller: `RecordHistoryEntryUseCase` enforces the
 * incognito suppression rule (FR-002) before invoking [recordVisit]; the repository itself
 * is unconditionally additive.
 *
 * Visit-time uniqueness: there is no UNIQUE constraint on URL — repeat visits to the same
 * URL produce separate rows distinguished by [HistoryEntry.visitedAt] (FR-004 / Q3).
 */
interface HistoryRepository {

    /**
     * Persist a new history entry. Always inserts a new row — repeat visits stay separate.
     *
     * @param url final settled URL.
     * @param title page title at load completion (may be empty).
     * @param visitedAt epoch milliseconds; caller passes [System.currentTimeMillis] at
     *  page-finish time.
     * @return the newly assigned row id.
     */
    suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long

    /**
     * Reverse-chronological observer — most recent first. Backed by Room's Flow query
     * observer; emits a fresh list on every committed insert/delete.
     */
    fun observeAll(): Flow<List<HistoryEntry>>

    /**
     * Spec 014 FR-001 (title backfill) — correct the title of an already-recorded row.
     *
     * A page's title routinely arrives after its load finishes, so [recordVisit] stores
     * whatever was known at page-finish and the caller patches it here once
     * `onReceivedTitle` fires for the same navigation. Returns rows updated (0 or 1).
     */
    suspend fun updateTitle(id: Long, title: String): Int

    /**
     * Hard-delete a single entry by id. Returns rows removed (0 or 1).
     */
    suspend fun deleteById(id: Long): Int

    /**
     * Spec 014 — delete every entry visited strictly before [cutoffMillis].
     *
     * Backs the history retention window — chosen by the user since Spec 016, 90 days by
     * default — which is what keeps the table, and therefore the in-memory list the History
     * screen holds, bounded over the app's lifetime. Returns rows removed.
     */
    suspend fun pruneOlderThan(cutoffMillis: Long): Int

    /**
     * Hard-delete every history entry. Caller is responsible for confirmation UI.
     * Returns rows removed.
     */
    suspend fun clearAll(): Int

    /**
     * Convenience for tests + future settings counters.
     */
    suspend fun count(): Int
}
