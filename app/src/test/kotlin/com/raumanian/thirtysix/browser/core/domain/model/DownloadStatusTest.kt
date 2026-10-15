package com.raumanian.thirtysix.browser.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the terminal/in-flight partition that both the row action matrix and the cancel
 * control's visibility read; defining it once keeps those two rules from drifting apart.
 */
class DownloadStatusTest {

    private val allStates = listOf(
        DownloadStatus.Pending,
        DownloadStatus.Running(bytesSoFar = 10L, totalBytes = 100L),
        DownloadStatus.Paused(bytesSoFar = 10L, totalBytes = 100L),
        DownloadStatus.Complete(),
        DownloadStatus.Failed(DownloadFailureCause.Generic),
        DownloadStatus.Cancelled,
        DownloadStatus.Missing,
    )

    @Test
    fun `terminal states are exactly complete failed cancelled and missing`() {
        val terminal = allStates.filter { it.isTerminal }
        assertEquals(
            setOf(
                DownloadStatus.Complete(),
                DownloadStatus.Failed(DownloadFailureCause.Generic),
                DownloadStatus.Cancelled,
                DownloadStatus.Missing,
            ),
            terminal.toSet(),
        )
    }

    @Test
    fun `in-flight states are exactly pending running and paused`() {
        val inFlight = allStates.filter { it.isInFlight }
        assertEquals(3, inFlight.size)
        assertTrue(inFlight.contains(DownloadStatus.Pending))
        assertTrue(inFlight.any { it is DownloadStatus.Running })
        assertTrue(inFlight.any { it is DownloadStatus.Paused })
    }

    @Test
    fun `the partition is total - every state is exactly one of terminal or in-flight`() {
        allStates.forEach { state ->
            assertTrue("$state belongs to neither half", state.isTerminal || state.isInFlight)
            assertFalse("$state belongs to both halves", state.isTerminal && state.isInFlight)
        }
    }

    @Test
    fun `every failure cause is terminal regardless of which cause it carries`() {
        listOf(
            DownloadFailureCause.InsufficientSpace,
            DownloadFailureCause.NetworkFailure,
            DownloadFailureCause.Generic,
        ).forEach { cause ->
            assertTrue(DownloadStatus.Failed(cause).isTerminal)
        }
    }

    @Test
    fun `a completed download carries the size the platform reported`() {
        // Every finished row must show its size, so `Complete` keeps the platform's total.
        val completed = DownloadStatus.Complete(totalBytes = 71L)

        assertEquals(71L, completed.totalBytes)
        assertTrue(completed.isTerminal)
    }

    @Test
    fun `a completed download with no reported size stays null rather than zero`() {
        // A handle the platform forgot resolves to Complete without a size;

        // null must survive so the UI says "unknown" instead of claiming 0 bytes.
        assertEquals(null, DownloadStatus.Complete().totalBytes)
    }

    @Test
    fun `null totalBytes is preserved so the UI can show indeterminate progress`() {
        val running = DownloadStatus.Running(bytesSoFar = 512L, totalBytes = null)
        assertEquals(null, running.totalBytes)
        assertEquals(512L, running.bytesSoFar)
    }
}
