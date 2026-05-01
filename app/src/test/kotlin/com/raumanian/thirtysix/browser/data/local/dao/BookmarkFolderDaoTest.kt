package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
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
class BookmarkFolderDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var folderDao: BookmarkFolderDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        folderDao = db.bookmarkFolderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert nested folder records parent_id`() = runTest {
        val parentId = folderDao.insert(BookmarkFolderEntity(name = "Parent", createdAt = 0L))
        val childId = folderDao.insert(
            BookmarkFolderEntity(name = "Child", parentId = parentId, createdAt = 0L),
        )
        val child = folderDao.getById(childId)!!
        assertEquals(parentId, child.parentId)
    }

    @Test
    fun `self-FK orphan-to-root when parent folder deleted`() = runTest {
        val parentId = folderDao.insert(BookmarkFolderEntity(name = "Parent", createdAt = 0L))
        val childId = folderDao.insert(
            BookmarkFolderEntity(name = "Child", parentId = parentId, createdAt = 0L),
        )

        folderDao.delete(folderDao.getById(parentId)!!)

        val orphaned = folderDao.getById(childId)
        assertNotNull("child folder must survive parent deletion", orphaned)
        assertNull("child folder parent_id must be NULL after orphan-to-root", orphaned!!.parentId)
    }

    @Test
    fun `observeChildren parent emits only direct children`() = runTest {
        val parentId = folderDao.insert(BookmarkFolderEntity(name = "Parent", createdAt = 0L))
        folderDao.insert(BookmarkFolderEntity(name = "ChildA", parentId = parentId, createdAt = 0L))
        folderDao.insert(BookmarkFolderEntity(name = "ChildB", parentId = parentId, createdAt = 0L))
        folderDao.insert(BookmarkFolderEntity(name = "RootFolder", createdAt = 0L))

        folderDao.observeChildren(parentId).test {
            val children = awaitItem()
            assertEquals(2, children.size)
            assertTrue(children.all { it.parentId == parentId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `count returns N after N inserts`() = runTest {
        assertEquals(0, folderDao.count())
        repeat(3) { i -> folderDao.insert(BookmarkFolderEntity(name = "F$i", createdAt = i.toLong())) }
        assertEquals(3, folderDao.count())
    }
}
