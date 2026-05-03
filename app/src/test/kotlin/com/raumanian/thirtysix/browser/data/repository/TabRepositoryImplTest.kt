package com.raumanian.thirtysix.browser.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.local.dao.TabDao
import com.raumanian.thirtysix.browser.data.local.dao.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 011 — exercises [TabRepositoryImpl] over an in-memory Room AppDatabase.
 *
 * Robolectric provides the Android Context (Spec 005 wiring; SDK 33 pin via
 * `app/src/test/resources/robolectric.properties`).
 */
@RunWith(AndroidJUnit4::class)
class TabRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var tabDao: TabDao
    private lateinit var repository: TabRepositoryImpl

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        tabDao = db.tabDao()
        repository = TabRepositoryImpl(
            tabDao = tabDao,
            homeUrl = HOME_URL,
            dispatchers = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeTabs on empty table seeds a fresh home tab`() = runTest {
        repository.observeTabs().test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals(HOME_URL, first.single().url)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, tabDao.count())
    }

    @Test
    fun `observeTabs orders by lastActiveAt DESC then id ASC`() = runTest {
        // Insert 3 tabs with non-monotonic lastActiveAt to verify the resort.
        tabDao.insert(entity(url = "https://a.com", lastActiveAt = 100L))
        tabDao.insert(entity(url = "https://b.com", lastActiveAt = 300L))
        tabDao.insert(entity(url = "https://c.com", lastActiveAt = 200L))

        repository.observeTabs().test {
            val list = awaitItem()
            assertEquals(
                listOf("https://b.com", "https://c.com", "https://a.com"),
                list.map { it.url },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createTab under cap returns Success with positive id`() = runTest {
        val result = repository.createTab("https://example.com/")

        assertTrue(result is Result.Success)
        val tab = (result as Result.Success).data
        assertTrue(tab.id > 0L)
        assertEquals("https://example.com/", tab.url)
        assertEquals(1, tabDao.count())
    }

    @Test
    fun `createTab at cap returns Error with MaxTabsReachedException`() = runTest {
        // Pre-fill to MAX_TABS via direct DAO inserts (faster than going through createTab).
        repeat(BrowserLimits.MAX_TABS) { i ->
            tabDao.insert(entity(url = "https://t$i.com", position = i))
        }
        assertEquals(BrowserLimits.MAX_TABS, tabDao.count())

        val result = repository.createTab("https://overflow.com/")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is MaxTabsReachedException)
        // Count must remain at the cap (no row inserted).
        assertEquals(BrowserLimits.MAX_TABS, tabDao.count())
    }

    @Test
    fun `closeTab on the last tab seeds a fresh home tab`() = runTest {
        val id = tabDao.insert(entity(url = "https://only.com", lastActiveAt = 1L))
        assertEquals(1, tabDao.count())

        repository.closeTab(id)

        assertEquals(1, tabDao.count())
        val remaining = tabDao.getAll().single()
        assertEquals(HOME_URL, remaining.url)
    }

    @Test
    fun `closeAllTabs wipes and seeds a single fresh home tab`() = runTest {
        tabDao.insert(entity(url = "https://a.com", lastActiveAt = 1L))
        tabDao.insert(entity(url = "https://b.com", lastActiveAt = 2L))
        tabDao.insert(entity(url = "https://c.com", lastActiveAt = 3L))
        assertEquals(3, tabDao.count())

        repository.closeAllTabs()

        assertEquals(1, tabDao.count())
        val remaining = tabDao.getAll().single()
        assertEquals(HOME_URL, remaining.url)
    }

    @Test
    fun `updateTabUrlAndTitle writes through and is observable`() = runTest {
        val id = tabDao.insert(entity(url = "https://before.com", title = ""))

        repository.updateTabUrlAndTitle(id, "https://after.com", "After")

        val refreshed = tabDao.getById(id)
        assertNotNull(refreshed)
        assertEquals("https://after.com", refreshed!!.url)
        assertEquals("After", refreshed.title)
    }

    @Test
    fun `updateTabUrlAndTitle on missing id is a no-op`() = runTest {
        assertNull(tabDao.getById(MISSING_ID))

        repository.updateTabUrlAndTitle(MISSING_ID, "https://after.com", "After")

        // No row materialized.
        assertNull(tabDao.getById(MISSING_ID))
    }

    private fun entity(
        url: String,
        title: String = "",
        position: Int = 0,
        createdAt: Long = 0L,
        lastActiveAt: Long = 0L,
    ) = TabEntity(
        url = url,
        title = title,
        position = position,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt,
    )

    private companion object {
        const val HOME_URL: String = "https://www.google.com/"
        const val MISSING_ID: Long = 9_999L
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
