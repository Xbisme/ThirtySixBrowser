package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TabSessionDao

    @Before
    fun setUp() {
        db = inMemoryAppDatabase()
        dao = db.tabSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newTab(): Long =
        db.tabDao().insert(
            TabEntity(url = "https://a.com", title = "A", position = 0, createdAt = 0L, lastActiveAt = 0L),
        )

    @Test
    fun `upsert then get returns the latest state`() = runTest {
        val id = newTab()
        dao.upsert(TabSessionEntity(id, byteArrayOf(1, 2), 10L))
        dao.upsert(TabSessionEntity(id, byteArrayOf(3), 20L))

        val row = dao.get(id)
        assertArrayEquals(byteArrayOf(3), row?.state)
        assertEquals(20L, row?.savedAt)
    }

    @Test
    fun `deleting the tab deletes its session`() = runTest {
        val id = newTab()
        dao.upsert(TabSessionEntity(id, byteArrayOf(1), 1L))

        db.tabDao().deleteAll()

        assertNull(dao.get(id))
    }

    @Test
    fun `deleteNotIn keeps only the given tabs`() = runTest {
        val kept = newTab()
        val dropped = newTab()
        dao.upsert(TabSessionEntity(kept, byteArrayOf(1), 1L))
        dao.upsert(TabSessionEntity(dropped, byteArrayOf(2), 1L))

        dao.deleteNotIn(listOf(kept))

        assertArrayEquals(byteArrayOf(1), dao.get(kept)?.state)
        assertNull(dao.get(dropped))
    }

    @Test
    fun `deleteNotIn with no ids deletes everything`() = runTest {
        val id = newTab()
        dao.upsert(TabSessionEntity(id, byteArrayOf(1), 1L))

        dao.deleteNotIn(emptyList())

        assertNull(dao.get(id))
    }
}
