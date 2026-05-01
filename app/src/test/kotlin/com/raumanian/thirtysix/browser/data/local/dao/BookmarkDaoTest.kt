package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var folderDao: BookmarkFolderDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        bookmarkDao = db.bookmarkDao()
        folderDao = db.bookmarkFolderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert returns positive id and getById round-trips`() = runTest {
        val now = System.currentTimeMillis()
        val id = bookmarkDao.insert(
            BookmarkEntity(title = "Example", url = "https://example.com", createdAt = now, sortOrder = 0L),
        )
        assertTrue("auto-id should be positive", id > 0L)

        val fetched = bookmarkDao.getById(id)
        assertNotNull(fetched)
        assertEquals("Example", fetched!!.title)
        assertEquals("https://example.com", fetched.url)
        assertNull(fetched.parentFolderId)
    }

    @Test
    fun `update mutates fields`() = runTest {
        val id = bookmarkDao.insert(
            BookmarkEntity(title = "Old", url = "https://old.com", createdAt = 0L, sortOrder = 0L),
        )
        val original = bookmarkDao.getById(id)!!
        bookmarkDao.update(original.copy(title = "New", url = "https://new.com"))
        val updated = bookmarkDao.getById(id)!!
        assertEquals("New", updated.title)
        assertEquals("https://new.com", updated.url)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val id = bookmarkDao.insert(
            BookmarkEntity(title = "Tmp", url = "https://tmp.com", createdAt = 0L, sortOrder = 0L),
        )
        val row = bookmarkDao.getById(id)!!
        bookmarkDao.delete(row)
        assertNull(bookmarkDao.getById(id))
    }

    @Test
    fun `count returns zero on empty and N after N inserts`() = runTest {
        assertEquals(0, bookmarkDao.count())
        repeat(3) { i ->
            bookmarkDao.insert(
                BookmarkEntity(
                    title = "B$i",
                    url = "https://b$i.com",
                    createdAt = i.toLong(),
                    sortOrder = i.toLong(),
                ),
            )
        }
        assertEquals(3, bookmarkDao.count())
    }

    @Test
    fun `observeByFolder null emits root bookmarks only`() = runTest {
        val folderId = folderDao.insert(BookmarkFolderEntity(name = "Folder", createdAt = 0L))
        bookmarkDao.insert(
            BookmarkEntity(title = "RootBM", url = "https://root.com", createdAt = 0L, sortOrder = 0L),
        )
        bookmarkDao.insert(
            BookmarkEntity(
                title = "ChildBM",
                url = "https://child.com",
                parentFolderId = folderId,
                createdAt = 0L,
                sortOrder = 0L,
            ),
        )

        bookmarkDao.observeByFolder(null).test {
            val rootList = awaitItem()
            assertEquals(1, rootList.size)
            assertEquals("RootBM", rootList[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByFolder emits fresh snapshot on insert`() = runTest {
        bookmarkDao.observeByFolder(null).test {
            assertEquals(0, awaitItem().size) // initial empty
            bookmarkDao.insert(
                BookmarkEntity(title = "First", url = "https://first.com", createdAt = 0L, sortOrder = 0L),
            )
            val afterInsert = awaitItem()
            assertEquals(1, afterInsert.size)
            assertEquals("First", afterInsert[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `FK orphan-to-root when parent folder deleted`() = runTest {
        val folderId = folderDao.insert(BookmarkFolderEntity(name = "Parent", createdAt = 0L))
        val bookmarkId = bookmarkDao.insert(
            BookmarkEntity(
                title = "Child",
                url = "https://child.com",
                parentFolderId = folderId,
                createdAt = 0L,
                sortOrder = 0L,
            ),
        )

        // sanity: bookmark currently references parent folder
        assertEquals(folderId, bookmarkDao.getById(bookmarkId)!!.parentFolderId)

        // delete parent → ON DELETE SET NULL fires
        folderDao.delete(folderDao.getById(folderId)!!)

        val orphaned = bookmarkDao.getById(bookmarkId)
        assertNotNull("bookmark must survive parent deletion (orphan-to-root)", orphaned)
        assertNull("parent_folder_id must be NULL after orphan-to-root", orphaned!!.parentFolderId)
    }

    @Test
    fun `same URL may be bookmarked multiple times (no unique constraint)`() = runTest {
        val a = bookmarkDao.insert(
            BookmarkEntity(title = "First", url = "https://same.com", createdAt = 0L, sortOrder = 0L),
        )
        val b = bookmarkDao.insert(
            BookmarkEntity(title = "Second", url = "https://same.com", createdAt = 1L, sortOrder = 1L),
        )
        assertTrue("two inserts of same URL must yield distinct ids", a != b)
        assertEquals(2, bookmarkDao.count())
    }
}
