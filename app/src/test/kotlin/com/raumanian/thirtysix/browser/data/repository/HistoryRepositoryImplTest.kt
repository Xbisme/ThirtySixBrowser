package com.raumanian.thirtysix.browser.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.dao.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 014 — covers [HistoryRepositoryImpl] full surface.
 */
@RunWith(AndroidJUnit4::class)
class HistoryRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepositoryImpl

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        repo = HistoryRepositoryImpl(
            dao = db.historyDao(),
            dispatchers = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recordVisit inserts a new row and returns its id`() = runTest {
        val id = repo.recordVisit("https://example.com", "Example", 1000L)
        assertTrue(id > 0L)
        assertEquals(1, repo.count())
    }

    @Test
    fun `observeAll emits in reverse chronological order`() = runTest {
        repo.recordVisit("https://a.example", "A", 1000L)
        repo.recordVisit("https://b.example", "B", 2000L)
        repo.recordVisit("https://c.example", "C", 3000L)
        repo.observeAll().test {
            val rows = awaitItem()
            assertEquals(3, rows.size)
            assertEquals("https://c.example", rows[0].url)
            assertEquals("https://b.example", rows[1].url)
            assertEquals("https://a.example", rows[2].url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteById removes only the targeted row and observer re-emits`() = runTest {
        val a = repo.recordVisit("https://a.example", "A", 1000L)
        repo.recordVisit("https://b.example", "B", 2000L)
        repo.observeAll().test {
            assertEquals(2, awaitItem().size)
            assertEquals(1, repo.deleteById(a))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll removes every row and observer re-emits empty list`() = runTest {
        repo.recordVisit("https://a.example", "A", 1000L)
        repo.recordVisit("https://b.example", "B", 2000L)
        repo.recordVisit("https://c.example", "C", 3000L)
        repo.observeAll().test {
            assertEquals(3, awaitItem().size)
            assertEquals(3, repo.clearAll())
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `count returns the live row total`() = runTest {
        assertEquals(0, repo.count())
        repo.recordVisit("https://example.com", "X", 1L)
        repo.recordVisit("https://example.com", "X", 2L)
        assertEquals(2, repo.count())
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
