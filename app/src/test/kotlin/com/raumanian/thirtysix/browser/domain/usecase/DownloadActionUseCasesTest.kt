package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 015 — the four per-entry actions (open · remove · delete · cancel).
 *
 * Grouped in one class because they share the same doubles and each is small; splitting
 * them would multiply setup without adding clarity.
 */
class DownloadActionUseCasesTest {

    // ---- FR-025 – FR-029 : open --------------------------------------------------

    @Test
    fun `open hands the recorded content URI to an external app`() = runTest {
        val gateway = RecordingGateway().apply { addFile("content://downloads/10") }
        val opener = RecordingFileOpener()

        val result = OpenDownloadedFileUseCase(gateway, opener)(testItem())

        assertEquals(OpenDownloadResult.Opened, result)
        assertEquals("content://downloads/10" to "application/pdf", opener.opened.single())
    }

    @Test
    fun `FR-028 - a download that has not finished is never opened`() = runTest {
        val opener = RecordingFileOpener()

        val result = OpenDownloadedFileUseCase(RecordingGateway(), opener)(
            testItem(status = DownloadStatus.Running(1L, 2L)),
        )

        assertEquals(OpenDownloadResult.NotComplete, result)
        assertTrue("no open attempt should be made", opener.opened.isEmpty())
    }

    @Test
    fun `FR-029 - a completed entry whose file is gone reports FileMissing`() = runTest {
        // Gateway knows no files, so neither the recorded URI nor a fresh one is readable.
        val result = OpenDownloadedFileUseCase(RecordingGateway(), RecordingFileOpener())(testItem())

        assertEquals(OpenDownloadResult.FileMissing, result)
    }

    @Test
    fun `FR-027 - a type nothing can handle reports NoAppAvailable rather than throwing`() = runTest {
        val gateway = RecordingGateway().apply { addFile("content://downloads/10") }

        val result = OpenDownloadedFileUseCase(gateway, RecordingFileOpener(succeeds = false))(testItem())

        assertEquals(OpenDownloadResult.NoAppAvailable, result)
    }

    @Test
    fun `open falls back to asking the platform when no URI was recorded`() = runTest {
        val gateway = RecordingGateway(contentUri = "content://downloads/fresh")
            .apply { addFile("content://downloads/fresh") }
        val opener = RecordingFileOpener()

        val result = OpenDownloadedFileUseCase(gateway, opener)(
            testItem(record = testRecord(localUri = null)),
        )

        assertEquals(OpenDownloadResult.Opened, result)
        assertEquals("content://downloads/fresh", opener.opened.single().first)
    }

    // ---- FR-031 / FR-034b : remove from list --------------------------------------

    @Test
    fun `FR-031 - removing an entry deletes the row and leaves the file alone`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway()

        val removed = RemoveDownloadRecordUseCase(repo)(testItem(record = testRecord(id = 1L)))

        assertTrue(removed)
        assertTrue(repo.current.isEmpty())
        assertTrue("the file must not be touched", gateway.deletedFiles.isEmpty())
    }

    @Test
    fun `FR-034b - removing an in-flight entry is refused so no transfer is orphaned`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))

        val removed = RemoveDownloadRecordUseCase(repo)(
            testItem(status = DownloadStatus.Running(1L, 10L), record = testRecord(id = 1L)),
        )

        assertFalse(removed)
        assertEquals("the row must survive", 1, repo.current.size)
    }

    @Test
    fun `removing an already-gone entry reports false`() = runTest {
        val repo = InMemoryDownloadsRepository(emptyList())

        assertFalse(RemoveDownloadRecordUseCase(repo)(testItem(record = testRecord(id = 99L))))
    }

    // ---- FR-032 : delete file ------------------------------------------------------

    @Test
    fun `FR-032 - deleting removes both the file and the row`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway()

        val deleted = DeleteDownloadedFileUseCase(gateway, repo)(testRecord(id = 1L))

        assertTrue(deleted)
        assertEquals("a.pdf", gateway.deletedFiles.single())
        assertTrue(repo.current.isEmpty())
    }

    @Test
    fun `a record with no stored URI is still deleted via its transfer handle`() = runTest {
        // Regression, found on an API 24 emulator during the G1 gate: this used to short-
        // circuit on `localUri == null` and delete only the row, so the app reported
        // "File deleted" over a file that was still on disk. The handle is always present
        // (FR-008b), so it — not the URI — is what the delete is keyed on.
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L, localUri = null)))
        val gateway = RecordingGateway()

        assertTrue(DeleteDownloadedFileUseCase(gateway, repo)(testRecord(id = 1L, localUri = null)))
        assertEquals("the platform must still be asked to delete", 1, gateway.deletedFiles.size)
        assertTrue(repo.current.isEmpty())
    }

    @Test
    fun `FR-032 - a delete the platform could not verify leaves the row alone`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway(deleteSucceeds = false)

        assertFalse(DeleteDownloadedFileUseCase(gateway, repo)(testRecord(id = 1L)))
        assertEquals("never claim success over a file still on disk", 1, repo.current.size)
    }

    @Test
    fun `a failed file delete leaves the row so the user can retry`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway(deleteSucceeds = false)

        assertFalse(DeleteDownloadedFileUseCase(gateway, repo)(testRecord(id = 1L)))
        assertEquals("the entry must stay visible", 1, repo.current.size)
    }

    // ---- FR-036 – FR-039 : cancel ---------------------------------------------------

    @Test
    fun `FR-037 - cancelling an in-flight transfer stops it and drops the row`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway()
        gateway.setStatus(10L, DownloadStatus.Running(5L, 100L))

        val cancelled = CancelDownloadUseCase(gateway, repo)(
            testItem(status = DownloadStatus.Running(5L, 100L), record = testRecord(id = 1L)),
        )

        assertTrue(cancelled)
        assertEquals(10L, gateway.cancelled.single())
        assertTrue("a cancelled row would otherwise resolve to Missing forever", repo.current.isEmpty())
    }

    @Test
    fun `FR-039 - a cancel that lands after completion leaves the file and reports nothing`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway()
        // The UI drew this row up to one poll interval ago; the transfer finished since.
        gateway.setStatus(10L, DownloadStatus.Complete())

        val cancelled = CancelDownloadUseCase(gateway, repo)(
            testItem(status = DownloadStatus.Running(99L, 100L), record = testRecord(id = 1L)),
        )

        assertFalse("must not claim a cancellation that did not happen", cancelled)
        assertTrue("the platform must not be asked to cancel", gateway.cancelled.isEmpty())
        assertEquals("the completed row must survive", 1, repo.current.size)
    }

    @Test
    fun `cancelling a transfer the platform has forgotten is refused`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway() // knows no statuses at all

        assertFalse(CancelDownloadUseCase(gateway, repo)(testItem(record = testRecord(id = 1L))))
        assertTrue(gateway.cancelled.isEmpty())
    }

    @Test
    fun `FR-038 - a failed platform cancel leaves the row intact`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway(cancelSucceeds = false)
        gateway.setStatus(10L, DownloadStatus.Running(1L, 10L))

        assertFalse(CancelDownloadUseCase(gateway, repo)(testItem(record = testRecord(id = 1L))))
        assertEquals(1, repo.current.size)
    }

    @Test
    fun `cancelling a failed entry is refused - there is nothing in flight`() = runTest {
        val repo = InMemoryDownloadsRepository(listOf(testRecord(id = 1L)))
        val gateway = RecordingGateway()
        gateway.setStatus(10L, DownloadStatus.Failed(DownloadFailureCause.NetworkFailure))

        assertFalse(CancelDownloadUseCase(gateway, repo)(testItem(record = testRecord(id = 1L))))
    }
}
