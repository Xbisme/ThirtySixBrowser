package com.raumanian.thirtysix.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 015 — pins the terminal/in-flight partition that FR-034a's action matrix and
 * FR-036a's cancel-control visibility both read. Defining it once is the only reason those
 * two rules cannot drift apart; this test is what keeps the definition honest.
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
        // Regression, found on an API 24 emulator during the G1 gate: `Complete` was a
        // data object carrying nothing, so the cursor mapper read the platform's total and
        // threw it away. Every finished row — the common case — rendered "Unknown size",
        // failing FR-020's requirement that each row show the file size.
        val completed = DownloadStatus.Complete(totalBytes = 71L)

        assertEquals(71L, completed.totalBytes)
        assertTrue(completed.isTerminal)
    }

    @Test
    fun `a completed download with no reported size stays null rather than zero`() {
        // The FR-024a fallback resolves a forgotten handle to Complete without a size;
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
