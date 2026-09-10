package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 014 — [PruneOldHistoryUseCase] enforces the
 * [BrowserLimits.MAX_HISTORY_DAYS] retention window, which is what keeps the history
 * table (and therefore the History screen's in-memory list) bounded over time.
 */
class PruneOldHistoryUseCaseTest {

    private val now = 10_000_000_000L
    private val window = TimeUnit.DAYS.toMillis(BrowserLimits.MAX_HISTORY_DAYS.toLong())

    @Test
    fun `entries older than the window are removed`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://old.example.com", "Old", now - window - 1L)
        repo.recordVisit("https://ancient.example.com", "Ancient", now - window * 3)

        assertEquals(2, PruneOldHistoryUseCase(repo)(now))
        assertEquals(0, repo.recorded.size)
    }

    @Test
    fun `entries inside the window are kept`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://today.example.com", "Today", now)
        repo.recordVisit("https://recent.example.com", "Recent", now - window + 1L)

        assertEquals(0, PruneOldHistoryUseCase(repo)(now))
        assertEquals(2, repo.recorded.size)
    }

    @Test
    fun `an entry exactly on the cutoff is kept (boundary is exclusive)`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://edge.example.com", "Edge", now - window)

        assertEquals(0, PruneOldHistoryUseCase(repo)(now))
        assertEquals(1, repo.recorded.size)
    }

    @Test
    fun `a mixed table keeps only what is inside the window`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://keep.example.com", "Keep", now - TimeUnit.DAYS.toMillis(1))
        repo.recordVisit("https://drop.example.com", "Drop", now - window - TimeUnit.DAYS.toMillis(1))

        assertEquals(1, PruneOldHistoryUseCase(repo)(now))
        assertEquals(listOf("https://keep.example.com"), repo.recorded.map { it.url })
    }
}
