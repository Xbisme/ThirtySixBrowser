package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.util.urlHostOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Always additive: callers suppress incognito visits, and repeat visits stay separate rows. */
interface HistoryRepository {

    /** [visitedAt] is epoch milliseconds; [title] may be empty. Returns the new row id. */
    suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long

    fun observeAll(): Flow<List<HistoryEntry>>

    /** The Room implementation overrides this with an indexed `LIMIT` query. */
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>> = observeAll().map { it.take(limit) }

    /** One entry per address, its latest visit, newest first. Room overrides it with a grouped query. */
    suspend fun getLatestVisitPerUrl(): List<HistoryEntry> = observeAll().first().distinctBy { it.url }

    /** Hosts of every recorded http/https address, so Fire can find the sites whose data to clear. */
    suspend fun distinctHosts(): Set<String> =
        observeAll().first().mapNotNullTo(HashSet()) { urlHostOrNull(it.url) }

    /** A page's title often arrives after its load finishes, so the row is patched here. */
    suspend fun updateTitle(id: Long, title: String): Int

    suspend fun deleteById(id: Long): Int

    /** Enforces the retention window, which keeps the table and the History list bounded. */
    suspend fun pruneOlderThan(cutoffMillis: Long): Int

    suspend fun clearAll(): Int

    suspend fun count(): Int
}
