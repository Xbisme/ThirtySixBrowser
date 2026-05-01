package com.raumanian.thirtysix.browser.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var tabDao: TabDao

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        tabDao = db.tabDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert returns positive id`() = runTest {
        val id = tabDao.insert(
            TabEntity(url = "https://a.com", title = "A", position = 0, createdAt = 0L, lastActiveAt = 0L),
        )
        assertTrue(id > 0L)
    }

    @Test
    fun `maxPosition is null on empty and reflects max after inserts`() = runTest {
        assertNull(tabDao.maxPosition())
        tabDao.insert(TabEntity(url = "https://a.com", title = "A", position = 0, createdAt = 0L, lastActiveAt = 0L))
        tabDao.insert(TabEntity(url = "https://b.com", title = "B", position = 5, createdAt = 0L, lastActiveAt = 0L))
        tabDao.insert(TabEntity(url = "https://c.com", title = "C", position = 2, createdAt = 0L, lastActiveAt = 0L))
        assertEquals(5, tabDao.maxPosition())
    }

    @Test
    fun `observeAll orders by position ASC`() = runTest {
        tabDao.insert(TabEntity(url = "https://3.com", title = "3", position = 3, createdAt = 0L, lastActiveAt = 0L))
        tabDao.insert(TabEntity(url = "https://1.com", title = "1", position = 1, createdAt = 0L, lastActiveAt = 0L))
        tabDao.insert(TabEntity(url = "https://2.com", title = "2", position = 2, createdAt = 0L, lastActiveAt = 0L))

        tabDao.observeAll().test {
            val list = awaitItem()
            assertEquals(listOf(1, 2, 3), list.map { it.position })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update mutates lastActiveAt`() = runTest {
        val id = tabDao.insert(
            TabEntity(url = "https://a.com", title = "A", position = 0, createdAt = 0L, lastActiveAt = 0L),
        )
        val original = tabDao.getById(id)!!
        tabDao.update(original.copy(lastActiveAt = 999L))
        assertEquals(999L, tabDao.getById(id)!!.lastActiveAt)
    }

    @Test
    fun `getAll returns same content as Flow snapshot`() = runTest {
        tabDao.insert(TabEntity(url = "https://1.com", title = "1", position = 0, createdAt = 0L, lastActiveAt = 0L))
        tabDao.insert(TabEntity(url = "https://2.com", title = "2", position = 1, createdAt = 0L, lastActiveAt = 0L))
        val list = tabDao.getAll()
        assertEquals(2, list.size)
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        tabDao.insert(TabEntity(url = "https://1.com", title = "1", position = 0, createdAt = 0L, lastActiveAt = 0L))
        tabDao.deleteAll()
        assertEquals(0, tabDao.count())
    }
}
