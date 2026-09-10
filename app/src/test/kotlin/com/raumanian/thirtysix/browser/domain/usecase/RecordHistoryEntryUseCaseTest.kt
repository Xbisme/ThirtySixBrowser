package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordHistoryEntryUseCaseTest {

    @Test
    fun `incognito visit is suppressed`() = runTest {
        val repo = FakeHistoryRepository()
        val useCase = RecordHistoryEntryUseCase(repo)
        useCase("https://example.com", "Example", isIncognito = true)
        assertNull(repo.lastRecorded)
        assertEquals(0, repo.recordCount)
    }

    @Test
    fun `non-incognito visit calls recordVisit once with passed url and title`() = runTest {
        val repo = FakeHistoryRepository()
        val useCase = RecordHistoryEntryUseCase(repo)
        val before = System.currentTimeMillis()
        useCase("https://example.com", "Example", isIncognito = false)
        val after = System.currentTimeMillis()

        assertEquals(1, repo.recordCount)
        val recorded = repo.lastRecorded
        assertNotNull(recorded)
        assertEquals("https://example.com", recorded?.first)
        assertEquals("Example", recorded?.second)
        val visitedAt = recorded?.third ?: 0L
        assertTrue("visitedAt should be in test interval", visitedAt in before..after)
    }

    @Test
    fun `repeat non-incognito calls produce repeat record invocations`() = runTest {
        val repo = FakeHistoryRepository()
        val useCase = RecordHistoryEntryUseCase(repo)
        useCase("https://example.com", "A", isIncognito = false)
        useCase("https://example.com", "A", isIncognito = false)
        useCase("https://example.com", "A", isIncognito = false)
        assertEquals(3, repo.recordCount)
    }

    @Test
    fun `empty title is forwarded unchanged`() = runTest {
        val repo = FakeHistoryRepository()
        val useCase = RecordHistoryEntryUseCase(repo)
        useCase("https://no-title.example", "", isIncognito = false)
        assertEquals("", repo.lastRecorded?.second)
    }

    private class FakeHistoryRepository : HistoryRepository {
        var recordCount = 0
            private set
        var lastRecorded: Triple<String, String, Long>? = null
            private set

        override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long {
            recordCount += 1
            lastRecorded = Triple(url, title, visitedAt)
            return recordCount.toLong()
        }

        override fun observeAll(): Flow<List<HistoryEntry>> = flowOf(emptyList())
        override suspend fun pruneOlderThan(cutoffMillis: Long): Int = 0
        override suspend fun updateTitle(id: Long, title: String): Int = 0
        override suspend fun deleteById(id: Long): Int = 0
        override suspend fun clearAll(): Int = 0
        override suspend fun count(): Int = recordCount
    }
}
