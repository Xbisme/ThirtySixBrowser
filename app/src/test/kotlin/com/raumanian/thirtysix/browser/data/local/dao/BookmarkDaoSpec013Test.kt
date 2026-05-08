package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 013 — covers the 6 new methods added to [BookmarkDao]: `observeByUrl`,
 * `countByUrl`, `deleteMostRecentByUrl`, `deleteByParentFolder`,
 * `searchByTitleOrUrl`, `countByFolder`.
 */
@RunWith(AndroidJUnit4::class)
class BookmarkDaoSpec013Test {

    private lateinit var db: AppDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        dao = db.bookmarkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeByUrl emits matching bookmarks then updates on insert`() = runTest {
        dao.observeByUrl("https://example.com").test {
            assertEquals(emptyList<BookmarkEntity>(), awaitItem())
            dao.insert(bookmark(url = "https://example.com", title = "First", createdAt = 100L))
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("First", first[0].title)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `countByUrl reflects current row count`() = runTest {
        assertEquals(0, dao.countByUrl("https://x.com"))
        dao.insert(bookmark(url = "https://x.com", title = "a"))
        dao.insert(bookmark(url = "https://x.com", title = "b"))
        assertEquals(2, dao.countByUrl("https://x.com"))
        assertEquals(0, dao.countByUrl("https://y.com"))
    }

    @Test
    fun `deleteMostRecentByUrl removes only newest matching row`() = runTest {
        val urlA = "https://multi.example.com"
        dao.insert(bookmark(url = urlA, title = "oldest", createdAt = 100L))
        dao.insert(bookmark(url = urlA, title = "middle", createdAt = 200L))
        dao.insert(bookmark(url = urlA, title = "newest", createdAt = 300L))
        dao.insert(bookmark(url = "https://other.example.com", title = "untouched"))

        val deleted = dao.deleteMostRecentByUrl(urlA)
        assertEquals(1, deleted)
        assertEquals(2, dao.countByUrl(urlA))
        assertEquals(1, dao.countByUrl("https://other.example.com"))
    }

    @Test
    fun `deleteMostRecentByUrl returns zero when no rows match`() = runTest {
        val deleted = dao.deleteMostRecentByUrl("https://nonexistent.example.com")
        assertEquals(0, deleted)
    }

    @Test
    fun `deleteByParentFolder removes all bookmarks under a folder`() = runTest {
        val folderDao = db.bookmarkFolderDao()
        val folderId = folderDao.insert(
            com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity(
                name = "F",
                parentId = null,
                createdAt = 1L,
            ),
        )
        dao.insert(bookmark(url = "https://a.com", title = "a", parentFolderId = folderId))
        dao.insert(bookmark(url = "https://b.com", title = "b", parentFolderId = folderId))
        dao.insert(bookmark(url = "https://root.com", title = "root", parentFolderId = null))

        val deleted = dao.deleteByParentFolder(folderId)
        assertEquals(2, deleted)
        // Root bookmark survives.
        assertEquals(1, dao.count())
    }

    @Test
    fun `searchByTitleOrUrl matches title substring (case-insensitive)`() = runTest {
        dao.insert(bookmark(url = "https://kotlin-lang.org", title = "Kotlin Docs"))
        dao.insert(bookmark(url = "https://example.com/foo", title = "Bar"))

        dao.searchByTitleOrUrl("KOTLIN").test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("Kotlin Docs", first[0].title)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `searchByTitleOrUrl matches url substring (case-insensitive)`() = runTest {
        dao.insert(bookmark(url = "https://github.com/anthropics", title = "Anthropic"))
        dao.insert(bookmark(url = "https://duckduckgo.com", title = "DDG"))

        dao.searchByTitleOrUrl("github").test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertTrue(first[0].url.contains("github"))
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `searchByTitleOrUrl returns recent first`() = runTest {
        dao.insert(bookmark(url = "https://a.com", title = "test1", createdAt = 100L))
        dao.insert(bookmark(url = "https://b.com", title = "test2", createdAt = 200L))
        dao.insert(bookmark(url = "https://c.com", title = "test3", createdAt = 300L))

        dao.searchByTitleOrUrl("test").test {
            val first = awaitItem()
            assertEquals(3, first.size)
            assertEquals("test3", first[0].title)
            assertEquals("test2", first[1].title)
            assertEquals("test1", first[2].title)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `countByFolder counts only bookmarks in that folder`() = runTest {
        val folderDao = db.bookmarkFolderDao()
        val folderA = folderDao.insert(
            com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity(
                name = "A",
                parentId = null,
                createdAt = 1L,
            ),
        )
        dao.insert(bookmark(url = "https://1.com", title = "1", parentFolderId = folderA))
        dao.insert(bookmark(url = "https://2.com", title = "2", parentFolderId = folderA))
        dao.insert(bookmark(url = "https://3.com", title = "3", parentFolderId = null))

        assertEquals(2, dao.countByFolder(folderA))
    }

    private fun bookmark(
        url: String,
        title: String,
        parentFolderId: Long? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): BookmarkEntity = BookmarkEntity(
        title = title,
        url = url,
        parentFolderId = parentFolderId,
        createdAt = createdAt,
        sortOrder = createdAt,
    )
}
