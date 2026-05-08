// CONTRACT — Spec 014 history-view
// This is the documented interface contract used by the planner. The implementation file
// will live at app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/HistoryRepository.kt
// (interface) + .../data/repository/HistoryRepositoryImpl.kt (impl). The implementation
// is bound by Hilt in app/.../di/HistoryModule.kt.

package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * History repository — Spec 014.
 *
 * Single source of truth for the persisted browsing-history slice. ViewModels MUST go
 * through this interface (Constitution §IV) and never touch HistoryDao directly.
 *
 * Recording is gated by the caller: [RecordHistoryEntryUseCase] enforces the incognito
 * suppression rule (FR-002) before invoking [recordVisit]. The repository itself is
 * unconditionally additive — it persists whatever the use case asks it to persist.
 *
 * Visit-time uniqueness: there is no UNIQUE constraint on URL — repeat visits to the
 * same URL produce separate rows distinguished by [visitedAt]. This is the
 * chronological-log invariant from Spec 005, reaffirmed by Spec 014 Q3.
 */
interface HistoryRepository {

    /**
     * Persist a new history entry. Always inserts a new row — repeat visits stay separate.
     *
     * @param url Final settled URL.
     * @param title Page title at load completion (may be empty).
     * @param visitedAt Epoch milliseconds; caller passes [System.currentTimeMillis] at
     *  `onPageFinished` time.
     * @return The newly assigned row id.
     */
    suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long

    /**
     * Reverse-chronological observer — most recent first.
     *
     * Backed by Room's Flow query observer; emits a fresh list on every committed
     * insert/delete. Consumers MUST NOT assume any ordering other than
     * `visitedAt DESC, id DESC` (matches HistoryDao.observeAll, Spec 005).
     */
    fun observeAll(): Flow<List<HistoryEntry>>

    /**
     * Hard-delete a single entry by id.
     *
     * @return number of rows removed (0 if id no longer present, 1 if removed).
     */
    suspend fun deleteById(id: Long): Int

    /**
     * Hard-delete every history entry. Caller is responsible for confirmation UI.
     *
     * @return number of rows removed.
     */
    suspend fun clearAll(): Int

    /**
     * Convenience for tests + future-spec settings counters.
     */
    suspend fun count(): Int
}
