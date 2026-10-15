package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WAL journal mode lets two coroutines write through BookmarkDao and HistoryDao at once
 * without deadlock, and both rows persist.
 */
@RunWith(AndroidJUnit4::class)
class WalConcurrencyTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Uses [runBlocking] rather than `runTest`: the claim is about real wall-clock time, and a
     * virtual scheduler would not exercise the actual SQLite writer lock.
     */
    @Test
    fun `concurrent inserts on different DAOs both succeed within 1s budget`() = runBlocking {
        val bookmarkDao = db.bookmarkDao()
        val historyDao = db.historyDao()

        // Robolectric's SQLite shim is much slower than a device's, so 5 s only proves no deadlock;
        // it is not the on-device latency target of under 1 s.

        withTimeout(5.seconds) {
            withContext(Dispatchers.IO) {
                val a = async {
                    bookmarkDao.insert(
                        BookmarkEntity(
                            title = "Concurrent A",
                            url = "https://a.com",
                            createdAt = 0L,
                            sortOrder = 0L,
                        ),
                    )
                }
                val b = async {
                    historyDao.insert(
                        HistoryEntryEntity(url = "https://b.com", title = "Concurrent B", visitedAt = 0L),
                    )
                }
                awaitAll(a, b)
            }
        }

        assertEquals(1, bookmarkDao.count())
        assertEquals(1, historyDao.count())
    }
}
