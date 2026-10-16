package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabSessionEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabSessionRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TabSessionRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryAppDatabase()
        repository = TabSessionRepositoryImpl(db.tabSessionDao(), TestDispatcherProvider())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newTab(): Long =
        db.tabDao().insert(
            TabEntity(url = "https://a.com", title = "A", position = 0, createdAt = 0L, lastActiveAt = 0L),
        )

    private suspend fun rowCount(): Int = db.query("SELECT COUNT(*) FROM ${TabSessionEntity.TABLE_NAME}", null)
        .use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun `a normal tab's session is written to the database and read back`() = runTest {
        val id = newTab()
        repository.save(TabSession(id, byteArrayOf(1, 2, 3), 5L))

        assertArrayEquals(byteArrayOf(1, 2, 3), repository.get(id)?.state)
        assertEquals(1, rowCount())
    }

    @Test
    fun `an incognito session never reaches the database`() = runTest {
        repository.save(TabSession(-1L, byteArrayOf(9), 5L))

        assertArrayEquals(byteArrayOf(9), repository.get(-1L)?.state)
        assertEquals(0, rowCount())
    }

    @Test
    fun `a state over the bound is not kept`() = runTest {
        val id = newTab()
        repository.save(TabSession(id, ByteArray(BrowserLimits.TAB_STATE_MAX_BYTES + 1), 5L))
        repository.save(TabSession(-2L, ByteArray(BrowserLimits.TAB_STATE_MAX_BYTES + 1), 5L))

        assertNull(repository.get(id))
        assertNull(repository.get(-2L))
    }

    @Test
    fun `a missing session reads as null`() = runTest {
        assertNull(repository.get(42L))
        assertNull(repository.get(-42L))
    }

    @Test
    fun `retainOnly drops normal and incognito sessions of closed tabs`() = runTest {
        val kept = newTab()
        val closed = newTab()
        repository.save(TabSession(kept, byteArrayOf(1), 1L))
        repository.save(TabSession(closed, byteArrayOf(2), 1L))
        repository.save(TabSession(-1L, byteArrayOf(3), 1L))
        repository.save(TabSession(-2L, byteArrayOf(4), 1L))

        repository.retainOnly(setOf(kept, -1L))

        assertArrayEquals(byteArrayOf(1), repository.get(kept)?.state)
        assertNull(repository.get(closed))
        assertArrayEquals(byteArrayOf(3), repository.get(-1L)?.state)
        assertNull(repository.get(-2L))
    }

    @Test
    fun `delete removes one session of either kind`() = runTest {
        val id = newTab()
        repository.save(TabSession(id, byteArrayOf(1), 1L))
        repository.save(TabSession(-1L, byteArrayOf(2), 1L))

        repository.delete(id)
        repository.delete(-1L)

        assertNull(repository.get(id))
        assertNull(repository.get(-1L))
    }

    @Test
    fun `clearAll empties both stores and counts the clear`() = runTest {
        val id = newTab()
        repository.save(TabSession(id, byteArrayOf(1), 1L))
        repository.save(TabSession(-1L, byteArrayOf(2), 1L))

        repository.clearAll()

        assertNull(repository.get(id))
        assertNull(repository.get(-1L))
        assertEquals(1L, repository.clears.value)
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
