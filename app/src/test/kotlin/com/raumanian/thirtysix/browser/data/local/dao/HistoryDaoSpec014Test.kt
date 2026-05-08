package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 014 — covers the additive [HistoryDao.deleteById] surface AND re-asserts the
 * Spec 005 chronological-log invariant under repeat-visit (FR-004 / Q3).
 */
@RunWith(AndroidJUnit4::class)
class HistoryDaoSpec014Test {

    private lateinit var db: AppDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        dao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `deleteById returns 1 when the row exists`() = runTest {
        val id = dao.insert(HistoryEntryEntity(0L, "https://example.com", "Example", 1000L))
        assertEquals(1, dao.deleteById(id))
        assertEquals(0, dao.count())
    }

    @Test
    fun `deleteById returns 0 when the row does not exist`() = runTest {
        assertEquals(0, dao.deleteById(99_999L))
    }

    @Test
    fun `deleteById removes only the targeted row, leaves siblings intact`() = runTest {
        val a = dao.insert(HistoryEntryEntity(0L, "https://a.example", "A", 1000L))
        dao.insert(HistoryEntryEntity(0L, "https://b.example", "B", 2000L))
        dao.insert(HistoryEntryEntity(0L, "https://c.example", "C", 3000L))
        assertEquals(1, dao.deleteById(a))
        assertEquals(2, dao.count())
    }

    @Test
    fun `repeat visits to the same URL produce separate rows distinguished by visited_at`() =
        runTest {
            dao.insert(HistoryEntryEntity(0L, "https://repeat.example", "R", 1000L))
            dao.insert(HistoryEntryEntity(0L, "https://repeat.example", "R", 2000L))
            dao.insert(HistoryEntryEntity(0L, "https://repeat.example", "R", 3000L))
            assertEquals(3, dao.count())
        }

    @Test
    fun `observeAll re-emits on insert and on deleteById`() = runTest {
        dao.observeAll().test {
            assertEquals(emptyList<HistoryEntryEntity>(), awaitItem())
            val id = dao.insert(HistoryEntryEntity(0L, "https://flow.example", "F", 1000L))
            assertEquals(1, awaitItem().size)
            dao.deleteById(id)
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
