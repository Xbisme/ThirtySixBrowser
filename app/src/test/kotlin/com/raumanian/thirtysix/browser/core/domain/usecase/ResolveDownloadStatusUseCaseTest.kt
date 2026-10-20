package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.core.domain.model.DownloadOrder
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.feature.downloads.fake.InMemoryDownloadsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fallback for downloads the platform has forgotten on its own schedule: the row must
 * still say something sensible once the platform handle no longer resolves.
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
    fun `a forgotten handle with the file present resolves to Complete`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(
            FakeGateway(statuses = emptyMap(), existingFiles = setOf("content://d/10")),
            InMemoryDownloadsRepository(),
        )

        assertEquals(DownloadStatus.Complete(), useCase(listOf(record())).single().status)
    }

    /**
     * When the platform forgets a transfer, the recorded content URI dies with the provider's
     * data while the file stays on disk, so presence must be answered from the real file.
     */
    @Test
    fun `the file is found by name when the recorded URI has died with the provider`() =
        runTest {
            val useCase = ResolveDownloadStatusUseCase(
                // The name is on disk; the recorded URI resolves to nothing.
                FakeGateway(statuses = emptyMap(), existingFiles = setOf("a.pdf")),
                InMemoryDownloadsRepository(),
            )

            assertEquals(
                DownloadStatus.Complete(),
                useCase(listOf(record(localUri = "content://d/10"))).single().status,
            )
        }

    /** A same-named file on disk may belong to another download, so a name alone proves nothing. */
    @Test
    fun `a forgotten handle with no recorded URI is Missing even when the name exists`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(
            FakeGateway(statuses = emptyMap(), existingFiles = setOf("a.pdf")),
            InMemoryDownloadsRepository(),
        )

        assertEquals(DownloadStatus.Missing, useCase(listOf(record(localUri = null))).single().status)
    }

    /**
     * The platform renames colliding downloads rather than overwriting, so the name on disk can
     * differ from the requested one; without storing it, three downloads of one filename would
     * display identically and all probe for the same file.
     */

    @Test
    fun `completion stores the name the platform actually used`() = runTest {
        val repository = InMemoryDownloadsRepository()
        val id = repository.insert(record(localUri = null))
        val gateway = FakeGateway(
            statuses = mapOf(10L to DownloadStatus.Complete(totalBytes = 12L)),
            contentUri = "content://d/10",
            resolvedName = "a-1.pdf",
        )

        val item = ResolveDownloadStatusUseCase(gateway, repository)(
            listOf(record(id = id, localUri = null)),
        ).single()

        assertEquals("a-1.pdf", item.record.fileName)
        assertEquals("content://d/10", item.record.localUri)
        assertEquals("the rename must be durable, not just displayed", "a-1.pdf", repository.current.single().fileName)
    }

    @Test
    fun `a platform that cannot name the file leaves the requested name alone`() = runTest {
        val repository = InMemoryDownloadsRepository()
        val id = repository.insert(record(localUri = null))
        val gateway = FakeGateway(
            statuses = mapOf(10L to DownloadStatus.Complete()),
            contentUri = "content://d/10",
            resolvedName = null,
        )

        val item = ResolveDownloadStatusUseCase(gateway, repository)(
            listOf(record(id = id, localUri = null)),
        ).single()

        assertEquals("a.pdf", item.record.fileName)
    }

    @Test
    fun `a forgotten handle with the file absent resolves to Missing`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(
            FakeGateway(statuses = emptyMap(), existingFiles = emptySet()),
            InMemoryDownloadsRepository(),
        )

        assertEquals(DownloadStatus.Missing, useCase(listOf(record())).single().status)
    }

    @Test
    fun `a forgotten handle that never recorded a file resolves to Missing`() = runTest {
        val useCase = ResolveDownloadStatusUseCase(FakeGateway(statuses = emptyMap()), InMemoryDownloadsRepository())

        assertEquals(DownloadStatus.Missing, useCase(listOf(record(localUri = null))).single().status)
    }

    @Test
    fun `the fallback NEVER yields an in-flight state`() = runTest {
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
    fun `a whole list resolves in one platform round trip`() = runTest {
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
        private val contentUri: String? = null,
        private val resolvedName: String? = null,
    ) : DownloadManagerGateway {
        var batchQueryCount = 0
        var fileExistsCount = 0

        override suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus> {
            batchQueryCount++
            return statuses.filterKeys { it in transferHandles }
        }

        override suspend fun fileExists(localUri: String?, fileName: String): Boolean {
            fileExistsCount++
            return fileName in existingFiles || localUri in existingFiles
        }

        override suspend fun enqueue(request: DownloadOrder): Long? = null
        override suspend fun cancel(transferHandle: Long) = false
        override suspend fun contentUriFor(transferHandle: Long): String? = contentUri
        override suspend fun resolvedFileNameFor(transferHandle: Long): String? = resolvedName
        override suspend fun deleteFile(transferHandle: Long, fileName: String) = false
        override suspend fun isAvailable() = true
    }
}
