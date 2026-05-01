package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        historyDao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert returns positive id`() = runTest {
        val id = historyDao.insert(
            HistoryEntryEntity(url = "https://example.com", title = "Example", visitedAt = 0L),
        )
        assertTrue(id > 0L)
    }

    @Test
    fun `chronological log invariant - same URL twice yields two rows`() = runTest {
        val a = historyDao.insert(HistoryEntryEntity(url = "https://x.com", title = "X1", visitedAt = 1L))
        val b = historyDao.insert(HistoryEntryEntity(url = "https://x.com", title = "X2", visitedAt = 2L))
        assertTrue("each visit must produce a new row", a != b)
        assertEquals(2, historyDao.count())
    }

    @Test
    fun `getRecent orders newest-first paginated`() = runTest {
        historyDao.insert(HistoryEntryEntity(url = "https://1.com", title = "1", visitedAt = 100L))
        historyDao.insert(HistoryEntryEntity(url = "https://2.com", title = "2", visitedAt = 200L))
        historyDao.insert(HistoryEntryEntity(url = "https://3.com", title = "3", visitedAt = 300L))

        val page = historyDao.getRecent(limit = 2, offset = 0)
        assertEquals(2, page.size)
        assertEquals(300L, page[0].visitedAt)
        assertEquals(200L, page[1].visitedAt)
    }

    @Test
    fun `observeAll Flow emits fresh snapshot on insert`() = runTest {
        historyDao.observeAll().test {
            assertEquals(0, awaitItem().size)
            historyDao.insert(HistoryEntryEntity(url = "https://a.com", title = "A", visitedAt = 1L))
            val afterInsert = awaitItem()
            assertEquals(1, afterInsert.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteInRange removes only matching rows`() = runTest {
        historyDao.insert(HistoryEntryEntity(url = "https://1.com", title = "1", visitedAt = 100L))
        historyDao.insert(HistoryEntryEntity(url = "https://2.com", title = "2", visitedAt = 200L))
        historyDao.insert(HistoryEntryEntity(url = "https://3.com", title = "3", visitedAt = 300L))

        val deleted = historyDao.deleteInRange(fromInclusive = 150L, toInclusive = 250L)
        assertEquals(1, deleted)
        assertEquals(2, historyDao.count())
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        historyDao.insert(HistoryEntryEntity(url = "https://a.com", title = "A", visitedAt = 0L))
        historyDao.insert(HistoryEntryEntity(url = "https://b.com", title = "B", visitedAt = 1L))
        historyDao.deleteAll()
        assertEquals(0, historyDao.count())
    }
}
