package com.raumanian.thirtysix.browser.presentation.downloads

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.data.repository.DownloadsRepositoryImpl
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.domain.usecase.SanitizeDownloadFileNameUseCase
import com.raumanian.thirtysix.browser.domain.usecase.StartDownloadResult
import com.raumanian.thirtysix.browser.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 015 T046 (US1 / FR-005, FR-008b) — the write path against **real Room on a device**,
 * through use case → repository → mapper → DAO.
 *
 * The JVM tests already cover the decision logic with fakes; what this adds is that the
 * record actually round-trips through SQLite on the `download_records` table the migration
 * created, on the API level under test.
 */
@RunWith(AndroidJUnit4::class)
class DownloadStartIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var startDownload: StartDownloadUseCase
    private lateinit var gateway: FakeGateway

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        gateway = FakeGateway(handle = 4242L)
        startDownload = StartDownloadUseCase(
            gateway = gateway,
            repository = DownloadsRepositoryImpl(db.downloadRecordDao(), UnconfinedDispatchers),
            sanitizeFileName = SanitizeDownloadFileNameUseCase(),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun acceptedDownload_persistsARecordWithTheSanitisedName() = runBlocking {
        val result = startDownload(
            url = "https://example.com/download",
            contentDisposition = "attachment; filename=\"../../../../etc/passwd\"",
            mimeType = "application/octet-stream",
            userAgent = "TestAgent/1.0",
        )

        assertEquals(StartDownloadResult.Started("passwd"), result)

        val stored = db.downloadRecordDao().observeAll().first().single()
        assertEquals("passwd", stored.fileName)
        assertEquals(4242L, stored.transferHandle)
        assertNull("localUri stays null until the transfer completes", stored.localUri)
    }

    @Test
    fun unavailableService_leavesTheTableEmpty() = runBlocking {
        val useCase = StartDownloadUseCase(
            gateway = FakeGateway(handle = null),
            repository = DownloadsRepositoryImpl(db.downloadRecordDao(), UnconfinedDispatchers),
            sanitizeFileName = SanitizeDownloadFileNameUseCase(),
        )

        val result = useCase("https://example.com/a.pdf", null, "application/pdf", "UA")

        assertEquals(StartDownloadResult.ServiceUnavailable, result)
        assertEquals("FR-008b — a handle-less row must never exist", 0, db.downloadRecordDao().count())
    }

    @Test
    fun twoDownloadsOfTheSameFile_produceTwoRows() = runBlocking {
        repeat(2) {
            startDownload("https://example.com/report.pdf", null, "application/pdf", "UA")
        }

        assertEquals(2, db.downloadRecordDao().count())
    }

    @Test
    fun aRecordSurvivesAReadBackThroughTheRepository() = runBlocking {
        startDownload("https://example.com/report.pdf", null, "application/pdf", "UA")

        val repository = DownloadsRepositoryImpl(db.downloadRecordDao(), UnconfinedDispatchers)
        val record = repository.observeAll().first().single()

        assertEquals("report.pdf", record.fileName)
        assertTrue(record.id > 0)
    }

    private class FakeGateway(private val handle: Long?) : DownloadManagerGateway {
        override suspend fun enqueue(request: DownloadRequest): Long? = handle
        override suspend fun queryStatus(transferHandle: Long): DownloadStatus? = null
        override suspend fun queryStatuses(transferHandles: List<Long>) = emptyMap<Long, DownloadStatus>()
        override suspend fun cancel(transferHandle: Long) = false
        override suspend fun contentUriFor(transferHandle: Long): String? = null
        override suspend fun fileExists(localUri: String) = false
        override suspend fun deleteFile(transferHandle: Long, fileName: String) = false
        override suspend fun isAvailable() = handle != null
    }

    private object UnconfinedDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
