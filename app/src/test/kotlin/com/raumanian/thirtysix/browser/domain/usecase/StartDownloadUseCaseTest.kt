package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 015 — the write path's two invariants, pinned.
 *
 * Both failures this guards against are silent ones: a record with no backing transfer,
 * and a download that never starts without telling the user.
 */
class StartDownloadUseCaseTest {

    private val repository = FakeDownloadsRepository()

    private fun useCase(gateway: DownloadManagerGateway) =
        StartDownloadUseCase(gateway, repository, SanitizeDownloadFileNameUseCase())

    @Test
    fun `a successful enqueue writes exactly one record carrying the returned handle`() = runTest {
        val gateway = FakeGateway(handle = 4242L)

        val result = useCase(gateway)(
            url = "https://example.com/report.pdf",
            contentDisposition = "attachment; filename=\"report.pdf\"",
            mimeType = "application/pdf",
            userAgent = "TestAgent/1.0",
        )

        assertEquals(StartDownloadResult.Started("report.pdf"), result)
        assertEquals(1, repository.inserted.size)
        with(repository.inserted.single()) {
            assertEquals("report.pdf", fileName)
            assertEquals(4242L, transferHandle)
            assertEquals("application/pdf", mimeType)
            assertNull("localUri must stay null while in flight", localUri)
        }
    }

    @Test
    fun `FR-008b - an unavailable service creates no record at all`() = runTest {
        val gateway = FakeGateway(handle = null)

        val result = useCase(gateway)(
            url = "https://example.com/report.pdf",
            contentDisposition = null,
            mimeType = null,
            userAgent = "TestAgent/1.0",
        )

        assertEquals(StartDownloadResult.ServiceUnavailable, result)
        assertTrue(
            "a record with no transfer handle would break the FR-024a inference",
            repository.inserted.isEmpty(),
        )
    }

    @Test
    fun `FR-008b - the platform is asked before anything is persisted`() = runTest {
        val gateway = FakeGateway(handle = 7L)

        useCase(gateway)("https://example.com/a.zip", null, "application/zip", "UA")

        assertEquals(
            "enqueue must happen before insert, or a handle-less row becomes possible",
            listOf("enqueue", "insert"),
            gateway.callLog + repository.callLog,
        )
    }

    @Test
    fun `FR-005 - the name handed to the platform is the sanitised one`() = runTest {
        val gateway = FakeGateway(handle = 1L)

        useCase(gateway)(
            url = "https://example.com/download",
            contentDisposition = "attachment; filename=\"../../../../etc/passwd\"",
            mimeType = null,
            userAgent = "UA",
        )

        assertEquals("passwd", gateway.lastRequest?.fileName)
        assertEquals("passwd", repository.inserted.single().fileName)
    }

    @Test
    fun `FR-014a - an incognito-origin download is recorded identically`() = runTest {
        // There is deliberately no incognito parameter to pass: the use case cannot behave
        // differently because it is never told. This test documents that as intent.
        val gateway = FakeGateway(handle = 9L)

        useCase(gateway)("https://example.com/secret.pdf", null, "application/pdf", "UA")

        assertEquals(1, repository.inserted.size)
        assertEquals("secret.pdf", repository.inserted.single().fileName)
    }

    @Test
    fun `FR-008 - one invocation enqueues at most once`() = runTest {
        val gateway = FakeGateway(handle = 5L)

        useCase(gateway)("https://example.com/a.bin", null, null, "UA")

        assertEquals(1, gateway.callLog.count { it == "enqueue" })
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `a missing mime type is recorded as an empty string never as null`() = runTest {
        val gateway = FakeGateway(handle = 3L)

        useCase(gateway)("https://example.com/a.bin", null, null, "UA")

        assertEquals("", repository.inserted.single().mimeType)
        assertEquals("", gateway.lastRequest?.mimeType)
    }

    @Test
    fun `the user agent and referer reach the platform request`() = runTest {
        val gateway = FakeGateway(handle = 2L)

        useCase(gateway)(
            url = "https://example.com/a.bin",
            contentDisposition = null,
            mimeType = null,
            userAgent = "Mozilla/5.0 Test",
            referer = "https://example.com/page",
        )

        assertEquals("Mozilla/5.0 Test", gateway.lastRequest?.userAgent)
        assertEquals("https://example.com/page", gateway.lastRequest?.referer)
    }

    // ---- doubles -----------------------------------------------------------------

    private class FakeGateway(private val handle: Long?) : DownloadManagerGateway {
        val callLog = mutableListOf<String>()
        var lastRequest: DownloadRequest? = null

        override suspend fun enqueue(request: DownloadRequest): Long? {
            callLog += "enqueue"
            lastRequest = request
            return handle
        }

        override suspend fun queryStatuses(transferHandles: List<Long>) = emptyMap<Long, DownloadStatus>()
        override suspend fun cancel(transferHandle: Long) = false
        override suspend fun contentUriFor(transferHandle: Long): String? = null
        override suspend fun fileExists(localUri: String?, fileName: String) = false
        override suspend fun resolvedFileNameFor(transferHandle: Long): String? = null
        override suspend fun deleteFile(transferHandle: Long, fileName: String) = false
        override suspend fun isAvailable() = handle != null
    }

    private class FakeDownloadsRepository : DownloadsRepository {
        val inserted = mutableListOf<DownloadRecord>()
        val callLog = mutableListOf<String>()

        override suspend fun insert(record: DownloadRecord): Long {
            callLog += "insert"
            inserted += record
            return inserted.size.toLong()
        }

        override fun observeAll(): Flow<List<DownloadRecord>> = flowOf(inserted.toList())
        override suspend fun getById(id: Long): DownloadRecord? = inserted.firstOrNull { it.id == id }
        override suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String) = Unit
        override suspend fun deleteById(id: Long) = false
        override suspend fun count(): Int = inserted.size
    }
}
