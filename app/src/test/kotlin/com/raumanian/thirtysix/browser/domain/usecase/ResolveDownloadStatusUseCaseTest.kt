package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 015 FR-024a – FR-024c — the fallback that the whole hybrid design rests on.
 *
 * The premise of the source-of-truth decision is that the platform forgets downloads on its
 * own schedule. These tests are what make "and then the row still says something sensible"
 * true rather than aspirational.
 */
class ResolveDownloadStatusUseCaseTest {

    private fun record(id: Long = 1L, handle: Long = 10L, localUri: String? = "content://d/10") =
        DownloadRecord(
            id = id,
            sourceUrl = "https://example.com/a.pdf",
            fileName = "a.pdf",
            mimeType = "application/pdf",
            createdAt = 1_000L,
            transferHandle = handle,
            localUri = localUri,
        )

    @Test
    fun `a status the platform reports is used verbatim`() = runTest {
        val reported = mapOf(10L to DownloadStatus.Running(bytesSoFar = 5L, totalBytes = 10L))
        val useCase = ResolveDownloadStatusUseCase(
            FakeGateway(statuses = reported),
            InMemoryDownloadsRepository(),
        )

        val items = useCase(listOf(record()))

        assertEquals(DownloadStatus.Running(5L, 10L), items.single().status)
    }

    @Test
    fun `FR-024a - a forgotten handle with the file present resolves to Complete`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(
            FakeGateway(statuses = emptyMap(), existingFiles = setOf("content://d/10")),
            InMemoryDownloadsRepository(),
        )

        assertEquals(DownloadStatus.Complete(), useCase(listOf(record())).single().status)
    }

    @Test
    fun `FR-024a - a forgotten handle with the file absent resolves to Missing`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(
            FakeGateway(statuses = emptyMap(), existingFiles = emptySet()),
            InMemoryDownloadsRepository(),
        )

        assertEquals(DownloadStatus.Missing, useCase(listOf(record())).single().status)
    }

    @Test
    fun `FR-024a - a forgotten handle that never recorded a file resolves to Missing`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(FakeGateway(statuses = emptyMap()), InMemoryDownloadsRepository())

        assertEquals(DownloadStatus.Missing, useCase(listOf(record(localUri = null))).single().status)
    }

    @Test
    fun `FR-024a - the fallback NEVER yields an in-flight state`() = runTest {
        // A transfer the platform has forgotten is not running, whatever the last thing we
        // saw claimed. This is the assertion that keeps a stale spinner off the screen.
        listOf(
            FakeGateway(statuses = emptyMap(), existingFiles = setOf("content://d/10")),
            FakeGateway(statuses = emptyMap(), existingFiles = emptySet()),
        ).forEach { gateway ->
            val useCase = ResolveDownloadStatusUseCase(gateway, InMemoryDownloadsRepository())
            val status = useCase(listOf(record())).single().status
            assertTrue("fallback produced $status, which is in flight", status.isTerminal)
            assertFalse(status is DownloadStatus.Running)
            assertFalse(status is DownloadStatus.Paused)
            assertFalse(status == DownloadStatus.Pending)
        }
    }

    @Test
    fun `FR-021 - a whole list resolves in one platform round trip`() = runTest {
        val gateway = FakeGateway(
            statuses = (1L..5L).associate { it * 10 to DownloadStatus.Complete() },
        )
        val useCase = ResolveDownloadStatusUseCase(gateway, InMemoryDownloadsRepository())

        useCase((1L..5L).map { record(id = it, handle = it * 10) })

        assertEquals("N rows must not cost N queries", 1, gateway.batchQueryCount)
    }

    @Test
    fun `file presence is only probed for handles the platform has forgotten`() = runTest {
        val gateway = FakeGateway(statuses = mapOf(10L to DownloadStatus.Complete()))
        val useCase = ResolveDownloadStatusUseCase(gateway, InMemoryDownloadsRepository())

        useCase(listOf(record(handle = 10L)))

        assertEquals("no filesystem probe should happen on the common path", 0, gateway.fileExistsCount)
    }

    @Test
    fun `a mixed list resolves each row independently`() = runTest {
        val gateway = FakeGateway(
            statuses = mapOf(10L to DownloadStatus.Running(1L, 2L)),
            existingFiles = setOf("content://d/20"),
        )
        val useCase = ResolveDownloadStatusUseCase(gateway, InMemoryDownloadsRepository())

        val items = useCase(
            listOf(
                record(id = 1L, handle = 10L, localUri = "content://d/10"),
                record(id = 2L, handle = 20L, localUri = "content://d/20"),
                record(id = 3L, handle = 30L, localUri = "content://d/30"),
            ),
        )

        assertEquals(DownloadStatus.Running(1L, 2L), items[0].status)
        assertEquals(DownloadStatus.Complete(), items[1].status)
        assertEquals(DownloadStatus.Missing, items[2].status)
    }

    @Test
    fun `an empty list short-circuits without touching the platform`() = runTest {
        val gateway = FakeGateway(statuses = emptyMap())

        val useCase = ResolveDownloadStatusUseCase(gateway, InMemoryDownloadsRepository())
        assertEquals(emptyList<Any>(), useCase(emptyList()))
        assertEquals(0, gateway.batchQueryCount)
    }

    @Test
    fun `a reported failure keeps its cause`() = runTest {
        val gateway = FakeGateway(
            statuses = mapOf(10L to DownloadStatus.Failed(DownloadFailureCause.InsufficientSpace)),
        )

        val useCase = ResolveDownloadStatusUseCase(gateway, InMemoryDownloadsRepository())
        val status = useCase(listOf(record())).single().status

        assertEquals(DownloadStatus.Failed(DownloadFailureCause.InsufficientSpace), status)
    }

    private class FakeGateway(
        private val statuses: Map<Long, DownloadStatus>,
        private val existingFiles: Set<String> = emptySet(),
    ) : DownloadManagerGateway {
        var batchQueryCount = 0
        var fileExistsCount = 0

        override suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus> {
            batchQueryCount++
            return statuses.filterKeys { it in transferHandles }
        }

        override suspend fun queryStatus(transferHandle: Long): DownloadStatus? = statuses[transferHandle]

        override suspend fun fileExists(localUri: String): Boolean {
            fileExistsCount++
            return localUri in existingFiles
        }

        override suspend fun enqueue(request: DownloadRequest): Long? = null
        override suspend fun cancel(transferHandle: Long) = false
        override suspend fun contentUriFor(transferHandle: Long): String? = null
        override suspend fun deleteFile(transferHandle: Long, fileName: String) = false
        override suspend fun isAvailable() = true
    }
}
