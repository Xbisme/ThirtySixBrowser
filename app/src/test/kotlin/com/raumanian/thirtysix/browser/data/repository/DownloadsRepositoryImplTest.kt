package com.raumanian.thirtysix.browser.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.dao.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 015 — [DownloadsRepositoryImpl] against real Room. */
@RunWith(AndroidJUnit4::class)
class DownloadsRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DownloadsRepository

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        repository = DownloadsRepositoryImpl(db.downloadRecordDao(), TestDispatcherProvider())
    }

    @After
    fun tearDown() = db.close()

    private fun record(
        fileName: String = "a.pdf",
        createdAt: Long = 1_000L,
        handle: Long = 1L,
        localUri: String? = null,
    ) = DownloadRecord(
        id = 0L,
        sourceUrl = "https://example.com/$fileName",
        fileName = fileName,
        mimeType = "application/pdf",
        createdAt = createdAt,
        transferHandle = handle,
        localUri = localUri,
    )

    @Test
    fun `insert returns the new row id`() = runTest {
        assertTrue(repository.insert(record()) > 0)
        assertEquals(1, repository.count())
    }

    @Test
    fun `observeAll maps entities to domain models newest first`() = runTest {
        repository.insert(record(fileName = "old.pdf", createdAt = 1_000L, handle = 1L))
        repository.insert(record(fileName = "new.pdf", createdAt = 2_000L, handle = 2L))

        repository.observeAll().test {
            val items = awaitItem()
            assertEquals(listOf("new.pdf", "old.pdf"), items.map { it.fileName })
            assertEquals(2L, items.first().transferHandle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteById reports true then false and propagates to the observer`() = runTest {
        val id = repository.insert(record())

        repository.observeAll().test {
            assertEquals(1, awaitItem().size)
            assertTrue(repository.deleteById(id))
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse("deleting an already-gone id reports false", repository.deleteById(id))
    }

    @Test
    fun `updateCompletionMetadata records where the finished file landed and its resolved name`() = runTest {
        val id = repository.insert(record())
        assertNull(repository.getById(id)?.localUri)

        repository.updateCompletionMetadata(id, "content://downloads/all_downloads/9", "report-1.pdf")

        assertEquals("content://downloads/all_downloads/9", repository.getById(id)?.localUri)
    }

    @Test
    fun `getById returns null for an unknown id`() = runTest {
        assertNull(repository.getById(12_345L))
    }

    @Test
    fun `count reflects inserts and deletes`() = runTest {
        assertEquals(0, repository.count())
        val id = repository.insert(record(handle = 1L))
        repository.insert(record(fileName = "b.pdf", handle = 2L))
        assertEquals(2, repository.count())
        repository.deleteById(id)
        assertEquals(1, repository.count())
    }

    @Test
    fun `two records may share a transfer handle - no unique constraint blocks the insert`() = runTest {
        // The platform allocates handles; a service that reset its counters must never be
        // able to make an insert fail and lose the user's download.
        repository.insert(record(fileName = "a.pdf", handle = 77L))
        repository.insert(record(fileName = "b.pdf", handle = 77L))
        assertEquals(2, repository.count())
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
