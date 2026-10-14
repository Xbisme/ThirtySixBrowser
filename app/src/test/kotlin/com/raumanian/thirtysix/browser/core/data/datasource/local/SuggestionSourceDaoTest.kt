package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 022 data-model §4 — the two one-shot reads behind address suggestions. */
@RunWith(AndroidJUnit4::class)
class SuggestionSourceDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `latest visit per url keeps one row per address, its newest visit, newest first`() = runTest {
        val dao = db.historyDao()
        dao.insert(HistoryEntryEntity(0L, "https://a.example", "A old", 1_000L))
        dao.insert(HistoryEntryEntity(0L, "https://b.example", "B", 2_000L))
        dao.insert(HistoryEntryEntity(0L, "https://a.example", "A new", 3_000L))
        dao.insert(HistoryEntryEntity(0L, "https://c.example", "C", 1_500L))

        val rows = dao.getLatestVisitPerUrl()

        assertEquals(listOf("https://a.example", "https://b.example", "https://c.example"), rows.map { it.url })
        assertEquals("A new", rows.first().title)
        assertEquals(3_000L, rows.first().visitedAt)
    }

    @Test
    fun `latest visit per url is empty for an empty history`() = runTest {
        assertEquals(emptyList<HistoryEntryEntity>(), db.historyDao().getLatestVisitPerUrl())
    }

    @Test
    fun `all bookmarks come newest saved first`() = runTest {
        val dao = db.bookmarkDao()
        dao.insert(bookmark("Old", "https://old.example", createdAt = 1_000L))
        dao.insert(bookmark("New", "https://new.example", createdAt = 3_000L))
        dao.insert(bookmark("Mid", "https://mid.example", createdAt = 2_000L))

        assertEquals(listOf("New", "Mid", "Old"), dao.getAllNewestFirst().map { it.title })
    }

    private fun bookmark(title: String, url: String, createdAt: Long) =
        BookmarkEntity(title = title, url = url, parentFolderId = null, createdAt = createdAt, sortOrder = createdAt)
}
