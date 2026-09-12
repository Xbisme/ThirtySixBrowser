package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeSettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 014 — [PruneOldHistoryUseCase] enforces the history retention window, which is what
 * keeps the history table (and therefore the History screen's in-memory list) bounded over
 * time. Spec 016 FR-024 — the window is the one persisted in settings, 90 days by default.
 */
class PruneOldHistoryUseCaseTest {

    private val now = 10_000_000_000L
    private val window = TimeUnit.DAYS.toMillis(HistoryRetention.Days90.days.toLong())

    /** The default settings snapshot, so the window is the documented 90 days. */
    private fun prune(repo: FakeHistoryRepository, settings: FakeSettingsRepository = FakeSettingsRepository()) =
        PruneOldHistoryUseCase(repo, settings)

    @Test
    fun `entries older than the window are removed`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://old.example.com", "Old", now - window - 1L)
        repo.recordVisit("https://ancient.example.com", "Ancient", now - window * 3)

        assertEquals(2, prune(repo)(now))
        assertEquals(0, repo.recorded.size)
    }

    @Test
    fun `entries inside the window are kept`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://today.example.com", "Today", now)
        repo.recordVisit("https://recent.example.com", "Recent", now - window + 1L)

        assertEquals(0, prune(repo)(now))
        assertEquals(2, repo.recorded.size)
    }

    @Test
    fun `an entry exactly on the cutoff is kept (boundary is exclusive)`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://edge.example.com", "Edge", now - window)

        assertEquals(0, prune(repo)(now))
        assertEquals(1, repo.recorded.size)
    }

    @Test
    fun `a mixed table keeps only what is inside the window`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://keep.example.com", "Keep", now - TimeUnit.DAYS.toMillis(1))
        repo.recordVisit("https://drop.example.com", "Drop", now - window - TimeUnit.DAYS.toMillis(1))

        assertEquals(1, prune(repo)(now))
        assertEquals(listOf("https://keep.example.com"), repo.recorded.map { it.url })
    }

    @Test
    fun `a 7-day window chosen in settings prunes everything older than 7 days`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://week.example.com", "Week", now - TimeUnit.DAYS.toMillis(6))
        repo.recordVisit("https://month.example.com", "Month", now - TimeUnit.DAYS.toMillis(30))
        val settings = FakeSettingsRepository(UserSettings.DEFAULT.copy(historyRetention = HistoryRetention.Days7))

        assertEquals(1, prune(repo, settings)(now))
        assertEquals(listOf("https://week.example.com"), repo.recorded.map { it.url })
    }
}
