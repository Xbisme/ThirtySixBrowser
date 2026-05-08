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
     * Hard-delete a single entry by id. Returns rows removed (0 or 1).
     */
    suspend fun deleteById(id: Long): Int

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
