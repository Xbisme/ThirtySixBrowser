package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 014 T066 — [DeleteHistoryEntryUseCase] is a pass-through onto
 * `HistoryRepository.deleteById`; these cases pin the row-count contract
 * (FR-020) and the "siblings for the same URL survive" invariant (Q3).
 */
class DeleteHistoryEntryUseCaseTest {

    @Test
    fun `delete removes the targeted row and returns 1`() = runTest {
        val repo = FakeHistoryRepository()
        val id = repo.recordVisit("https://example.com", "Example", 1_000L)

        val removed = DeleteHistoryEntryUseCase(repo)(id)

        assertEquals(1, removed)
        assertEquals(0, repo.recorded.size)
    }

    @Test
    fun `delete of an unknown id returns 0 and leaves the list untouched`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", 1_000L)

        val removed = DeleteHistoryEntryUseCase(repo)(id = 999L)

        assertEquals(0, removed)
        assertEquals(1, repo.recorded.size)
    }

    @Test
    fun `deleting one visit leaves other visits to the same URL intact (FR-020 Q3)`() = runTest {
        val repo = FakeHistoryRepository()
        val first = repo.recordVisit("https://example.com", "Example", 1_000L)
        repo.recordVisit("https://example.com", "Example", 2_000L)

        DeleteHistoryEntryUseCase(repo)(first)

        assertEquals(1, repo.recorded.size)
        assertEquals(2_000L, repo.recorded.single().visitedAt)
    }
}
