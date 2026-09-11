package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.HistoryRetentionChangeResult
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeSettingsRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 016 T082 — [ChangeHistoryRetentionUseCase]: write before prune, the three outcomes, and
 * SC-010's "nothing older than the window remains" for every one of the four windows.
 */
class ChangeHistoryRetentionUseCaseTest {

    private val log = mutableListOf<String>()
    private val settings = FakeSettingsRepository(callLog = log)
    private val history = FakeHistoryRepository(log)
    private val useCase = ChangeHistoryRetentionUseCase(settings, history)

    @Test
    fun `the new window is saved before history is pruned`() = runTest {
        useCase(HistoryRetention.Days30, NOW)

        assertEquals(listOf(FakeSettingsRepository.CALL_SET_HISTORY_RETENTION, FakeHistoryRepository.CALL_PRUNE), log)
    }

    @Test
    fun `a failed write returns NotSaved and deletes nothing`() = runTest {
        settings.failNextWrite = true
        history.recordVisit("https://old.example.com", "Old", NOW - days(365))

        val result = useCase(HistoryRetention.Days7, NOW)

        assertEquals(HistoryRetentionChangeResult.NotSaved, result)
        assertFalse(FakeHistoryRepository.CALL_PRUNE in log)
        assertEquals(1, history.recorded.size)
    }

    @Test
    fun `a failed prune returns SavedPruneDeferred and the new window stays saved`() = runTest {
        history.pruneError = IOException("disk")

        val result = useCase(HistoryRetention.Days7, NOW)

        assertEquals(HistoryRetentionChangeResult.SavedPruneDeferred, result)
        assertEquals(HistoryRetention.Days7, settings.current.historyRetention)
    }

    @Test
    fun `lengthening removes nothing that was inside the old window`() = runTest {
        history.recordVisit("https://recent.example.com", "Recent", NOW - days(1))
        history.recordVisit("https://edge.example.com", "Edge", NOW - days(89))

        val result = useCase(HistoryRetention.Days180, NOW)

        assertEquals(HistoryRetentionChangeResult.Applied(rowsRemoved = 0), result)
        assertEquals(2, history.recorded.size)
    }

    /** SC-010 — parameterised over all four windows with rows one hour either side of the cutoff. */
    @Test
    fun `every window removes exactly the rows outside it`() = runTest {
        HistoryRetention.entries.forEach { retention ->
            val repository = FakeHistoryRepository()
            val window = days(retention.days)
            repository.recordVisit(INSIDE_URL, "Inside", NOW - window + ONE_HOUR_MS)
            repository.recordVisit(OUTSIDE_URL, "Outside", NOW - window - ONE_HOUR_MS)

            val result = ChangeHistoryRetentionUseCase(FakeSettingsRepository(), repository)(retention, NOW)

            assertEquals("${retention.days} days", HistoryRetentionChangeResult.Applied(rowsRemoved = 1), result)
            assertEquals("${retention.days} days", listOf(INSIDE_URL), repository.recorded.map { it.url })
        }
    }

    @Test
    fun `cancellation during the prune propagates`() = runTest {
        history.pruneError = CancellationException("cancelled")

        val thrown = runCatching { useCase(HistoryRetention.Days30, NOW) }.exceptionOrNull()

        assertTrue("got $thrown", thrown is CancellationException)
    }

    private fun days(count: Int): Long = TimeUnit.DAYS.toMillis(count.toLong())

    private companion object {
        /** Far enough from the epoch that 180 days back is still positive. */
        const val NOW: Long = 1_800_000_000_000L
        const val ONE_HOUR_MS: Long = 3_600_000L
        const val INSIDE_URL = "https://inside.example.com/"
        const val OUTSIDE_URL = "https://outside.example.com/"
    }
}
