package com.raumanian.thirtysix.browser.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveHistoryEntriesUseCaseTest {

    @Test
    fun `invoke passes through the underlying flow unchanged`() = runTest {
        val expected = listOf(
            HistoryEntry(1L, "https://a.example", "A", 1L),
            HistoryEntry(2L, "https://b.example", "B", 2L),
        )
        val useCase = ObserveHistoryEntriesUseCase(StaticRepository(expected))
        useCase().test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }

    private class StaticRepository(private val rows: List<HistoryEntry>) : HistoryRepository {
        override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long = 0L
        override fun observeAll(): Flow<List<HistoryEntry>> = flowOf(rows)
        override suspend fun deleteById(id: Long): Int = 0
        override suspend fun clearAll(): Int = 0
        override suspend fun count(): Int = rows.size
    }
}
