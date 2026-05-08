package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 013 — covers the 2 new methods added to [BookmarkFolderDao]:
 * `getDirectChildren`, `deleteById`.
 */
@RunWith(AndroidJUnit4::class)
class BookmarkFolderDaoSpec013Test {

    private lateinit var db: AppDatabase
    private lateinit var dao: BookmarkFolderDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        dao = db.bookmarkFolderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getDirectChildren null returns root-level folders only`() = runTest {
        val rootA = dao.insert(BookmarkFolderEntity(name = "A", parentId = null, createdAt = 1L))
        val rootB = dao.insert(BookmarkFolderEntity(name = "B", parentId = null, createdAt = 2L))
        dao.insert(BookmarkFolderEntity(name = "A.1", parentId = rootA, createdAt = 3L))

        val rootChildren = dao.getDirectChildren(null)
        assertEquals(2, rootChildren.size)
        val ids = rootChildren.map { it.id }.toSet()
        assertEquals(setOf(rootA, rootB), ids)
    }

    @Test
    fun `getDirectChildren of specific parent returns only its direct children`() = runTest {
        val rootA = dao.insert(BookmarkFolderEntity(name = "A", parentId = null, createdAt = 1L))
        val a1 = dao.insert(BookmarkFolderEntity(name = "A.1", parentId = rootA, createdAt = 2L))
        dao.insert(BookmarkFolderEntity(name = "A.1.1", parentId = a1, createdAt = 3L))
        dao.insert(BookmarkFolderEntity(name = "A.2", parentId = rootA, createdAt = 4L))

        val children = dao.getDirectChildren(rootA)
        assertEquals(2, children.size) // A.1 and A.2 — NOT A.1.1
    }

    @Test
    fun `deleteById removes the folder and returns 1`() = runTest {
        val id = dao.insert(BookmarkFolderEntity(name = "X", parentId = null, createdAt = 1L))
        assertEquals(1, dao.deleteById(id))
        assertEquals(null, dao.getById(id))
    }

    @Test
    fun `deleteById on non-existent id returns 0`() = runTest {
        val deleted = dao.deleteById(99_999L)
        assertEquals(0, deleted)
    }
}
