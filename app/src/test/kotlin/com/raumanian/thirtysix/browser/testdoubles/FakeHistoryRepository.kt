package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Spec 014 — minimal in-memory [HistoryRepository] for ViewModel-level tests.
 * Records visits into a backing list flow; supports observing, deleting by id,
 * clearing, and counting. Sufficient for any test that needs to verify the
 * recorder fires (or doesn't fire) without standing up a real Room DB.
 *
 * Spec 016 — [clearAll] and [pruneOlderThan] append to an optional [callLog] shared with other
 * fakes, and can be made to throw through [clearAllError] / [pruneError], for the ordering and
 * failure-boundary tests of `ClearBrowsingDataUseCase` and `ChangeHistoryRetentionUseCase`.
 */
class FakeHistoryRepository(
    private val callLog: MutableList<String> = mutableListOf(),
) : HistoryRepository {

    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var nextId: Long = 1L

    /** When set, [clearAll] throws it instead of clearing. */
    var clearAllError: Throwable? = null

    /** When set, [pruneOlderThan] throws it instead of pruning. */
    var pruneError: Throwable? = null

    val recorded: List<HistoryEntry>
        get() = state.value

    override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long {
        val id = nextId++
        state.value = listOf(HistoryEntry(id, url, title, visitedAt)) + state.value
        return id
    }

    override fun observeAll(): Flow<List<HistoryEntry>> = state.asStateFlow().map { it }

    override suspend fun updateTitle(id: Long, title: String): Int {
        val before = state.value
        state.value = before.map { if (it.id == id) it.copy(title = title) else it }
        return if (before.any { it.id == id }) 1 else 0
    }

    override suspend fun deleteById(id: Long): Int {
        val before = state.value.size
        state.value = state.value.filterNot { it.id == id }
        return before - state.value.size
    }

    override suspend fun pruneOlderThan(cutoffMillis: Long): Int {
        callLog += CALL_PRUNE
        pruneError?.let { throw it }
        val before = state.value.size
        state.value = state.value.filterNot { it.visitedAt < cutoffMillis }
        return before - state.value.size
    }

    override suspend fun clearAll(): Int {
        callLog += CALL_CLEAR_ALL
        clearAllError?.let { throw it }
        val before = state.value.size
        state.value = emptyList()
        return before
    }

    override suspend fun count(): Int = state.value.size

    companion object {
        const val CALL_CLEAR_ALL: String = "history.clearAll"
        const val CALL_PRUNE: String = "history.pruneOlderThan"
    }
}
