package com.raumanian.thirtysix.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 015 FR-034a / FR-034b — the action matrix, asserted as the table the spec states.
 *
 * quickstart.md G5 checks the same five-by-four grid by hand on a device; this is its
 * automated counterpart, so a regression is caught at build time rather than at gate time.
 */
class DownloadActionAvailabilityTest {

    private fun availability(status: DownloadStatus, filePresent: Boolean) =
        DownloadActionAvailability.forStatus(status, filePresent)

    @Test
    fun `in flight - only copy link is offered`() {
        listOf(
            DownloadStatus.Pending,
            DownloadStatus.Running(1L, 2L),
            DownloadStatus.Paused(1L, 2L),
        ).forEach { status ->
            val a = availability(status, filePresent = false)
            assertFalse("$status should not offer open", a.canOpen)
            assertTrue("$status should offer copy link", a.canCopyLink)
            assertFalse("$status must NOT offer remove (FR-034b)", a.canRemoveFromList)
            assertFalse("$status should not offer delete", a.canDeleteFile)
        }
    }

    @Test
    fun `complete with the file present - everything is offered`() {
        assertEquals(
            DownloadActionAvailability(
                canOpen = true,
                canCopyLink = true,
                canRemoveFromList = true,
                canDeleteFile = true,
            ),
            availability(DownloadStatus.Complete(), filePresent = true),
        )
    }

    @Test
    fun `failed - copy link and remove only`() {
        val a = availability(DownloadStatus.Failed(DownloadFailureCause.Generic), filePresent = false)
        assertFalse(a.canOpen)
        assertTrue(a.canCopyLink)
        assertTrue(a.canRemoveFromList)
        assertFalse(a.canDeleteFile)
    }

    @Test
    fun `cancelled - copy link and remove only`() {
        val a = availability(DownloadStatus.Cancelled, filePresent = false)
        assertFalse(a.canOpen)
        assertTrue(a.canCopyLink)
        assertTrue(a.canRemoveFromList)
        assertFalse(a.canDeleteFile)
    }

    @Test
    fun `missing - copy link and remove only`() {
        val a = availability(DownloadStatus.Missing, filePresent = false)
        assertFalse(a.canOpen)
        assertTrue(a.canCopyLink)
        assertTrue(a.canRemoveFromList)
        assertFalse(a.canDeleteFile)
    }

    @Test
    fun `FR-034b - remove is withheld for every in-flight state and allowed for every terminal one`() {
        // The load-bearing cell of the whole matrix: it is what makes orphaning a running
        // transfer structurally impossible rather than merely unlikely.
        val inFlight = listOf(
            DownloadStatus.Pending,
            DownloadStatus.Running(0L, null),
            DownloadStatus.Paused(0L, null),
        )
        val terminal = listOf(
            DownloadStatus.Complete(),
            DownloadStatus.Failed(DownloadFailureCause.NetworkFailure),
            DownloadStatus.Cancelled,
            DownloadStatus.Missing,
        )
        inFlight.forEach { assertFalse("$it", availability(it, false).canRemoveFromList) }
        terminal.forEach { assertTrue("$it", availability(it, false).canRemoveFromList) }
    }

    @Test
    fun `copy link is offered in every single state`() {
        // The source address is recorded at insert time and never goes away, so there is no
        // state in which copying it is meaningless.
        listOf(
            DownloadStatus.Pending,
            DownloadStatus.Running(0L, null),
            DownloadStatus.Paused(0L, null),
            DownloadStatus.Complete(),
            DownloadStatus.Failed(DownloadFailureCause.Generic),
            DownloadStatus.Cancelled,
            DownloadStatus.Missing,
        ).forEach { assertTrue("$it", availability(it, false).canCopyLink) }
    }

    @Test
    fun `complete without a file on disk cannot be opened or deleted`() {
        val a = availability(DownloadStatus.Complete(), filePresent = false)
        assertFalse("nothing to open", a.canOpen)
        assertFalse("nothing to delete", a.canDeleteFile)
        assertTrue(a.canRemoveFromList)
    }
}
