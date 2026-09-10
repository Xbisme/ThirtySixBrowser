package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 015 — DAO surface for the `download_records` table added at schema v2. */
@RunWith(AndroidJUnit4::class)
class DownloadRecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadRecordDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        dao = db.downloadRecordDao()
    }

    @After
    fun tearDown() = db.close()

    private fun record(
        fileName: String = "a.pdf",
        createdAt: Long = 1_000L,
        handle: Long = 1L,
        localUri: String? = null,
    ) = DownloadRecordEntity(
        id = 0L,
        sourceUrl = "https://example.com/$fileName",
        fileName = fileName,
        mimeType = "application/pdf",
        createdAt = createdAt,
        transferHandle = handle,
        localUri = localUri,
    )

    @Test
    fun `insert returns a positive row id and the record reads back`() = runTest {
        val id = dao.insert(record())
        assertEquals(1, dao.count())
        assertEquals("a.pdf", dao.getById(id)?.fileName)
    }

    @Test
    fun `observeAll returns newest first`() = runTest {
        dao.insert(record(fileName = "old.pdf", createdAt = 1_000L, handle = 1L))
        dao.insert(record(fileName = "new.pdf", createdAt = 3_000L, handle = 2L))
        dao.insert(record(fileName = "mid.pdf", createdAt = 2_000L, handle = 3L))

        dao.observeAll().test {
            assertEquals(listOf("new.pdf", "mid.pdf", "old.pdf"), awaitItem().map { it.fileName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records sharing a createdAt still have a deterministic order`() = runTest {
        val first = dao.insert(record(fileName = "first.pdf", createdAt = 5_000L, handle = 1L))
        val second = dao.insert(record(fileName = "second.pdf", createdAt = 5_000L, handle = 2L))

        dao.observeAll().test {
            // id DESC is the tiebreaker, so the later insert sorts first and the list can
            // never reshuffle between emissions.
            assertEquals(listOf(second, first), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll re-emits on insert and on delete`() = runTest {
        dao.observeAll().test {
            assertEquals(0, awaitItem().size)
            val id = dao.insert(record())
            assertEquals(1, awaitItem().size)
            dao.deleteById(id)
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteById returns 1 for a hit and 0 for a miss`() = runTest {
        val id = dao.insert(record())
        assertEquals(1, dao.deleteById(id))
        assertEquals(0, dao.deleteById(id))
        assertEquals(0, dao.deleteById(99_999L))
    }

    @Test
    fun `updateLocalUri persists and returns the affected row count`() = runTest {
        val id = dao.insert(record())
        assertNull(dao.getById(id)?.localUri)

        assertEquals(1, dao.updateLocalUri(id, "content://downloads/1"))
        assertEquals("content://downloads/1", dao.getById(id)?.localUri)
        assertEquals(0, dao.updateLocalUri(99_999L, "content://downloads/2"))
    }

    @Test
    fun `getById returns null once the record is gone`() = runTest {
        val id = dao.insert(record())
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }
}
