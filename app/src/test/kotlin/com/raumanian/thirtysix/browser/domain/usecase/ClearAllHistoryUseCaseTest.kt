package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 014 T083 — [ClearAllHistoryUseCase] passes through to
 * `HistoryRepository.clearAll` and reports the number of rows wiped (FR-025).
 */
class ClearAllHistoryUseCaseTest {

    @Test
    fun `clearAll wipes every entry and returns the removed row count`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://a.example.com", "A", 1_000L)
        repo.recordVisit("https://b.example.com", "B", 2_000L)
        repo.recordVisit("https://c.example.com", "C", 3_000L)

        val removed = ClearAllHistoryUseCase(repo)()

        assertEquals(3, removed)
        assertEquals(0, repo.recorded.size)
    }

    @Test
    fun `clearAll on an already-empty history returns 0`() = runTest {
        val repo = FakeHistoryRepository()

        assertEquals(0, ClearAllHistoryUseCase(repo)())
    }
}
