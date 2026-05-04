package com.raumanian.thirtysix.browser.data.repository

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.testdoubles.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 012 — T008 unit tests for [IncognitoTabRepositoryImpl] per
 * [contracts/IncognitoTabRepository.contract.md].
 *
 * Pure JVM, no Robolectric required: the impl never touches Android APIs
 * directly — `CookieJarSnapshotManager` is the platform seam and is replaced
 * here with [FakeCookieJarSnapshotManager]. `TabRepository` is replaced with
 * [FakeTabRepository] (in-memory, auto-seeds a home tab on first
 * subscription so origin enumeration on 0→1 transition has content to read).
 */
class IncognitoTabRepositoryImplTest {

    private lateinit var cookieManager: FakeCookieJarSnapshotManager
    private lateinit var tabRepository: FakeTabRepository
    private lateinit var repository: IncognitoTabRepositoryImpl

    @Before
    fun setup() {
        cookieManager = FakeCookieJarSnapshotManager()
        tabRepository = FakeTabRepository()
        repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = cookieManager,
            tabRepository = tabRepository,
            dispatchers = TestDispatcherProvider(),
        )
    }

    @Test
    fun `1 - observeTabs starts empty`() = runTest {
        repository.observeTabs().test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `2 - createTab inserts tab with isIncognito true and negative id`() = runTest {
        val result = repository.createTab("https://example.com/")

        assertTrue(result is Result.Success)
        val tab = (result as Result.Success).data
        assertTrue("id should be strictly negative, got=${tab.id}", tab.id < 0L)
        assertTrue(tab.isIncognito)
        assertEquals("https://example.com/", tab.url)
        assertEquals(0, tab.position)

        val list = repository.observeTabs().first()
        assertEquals(1, list.size)
        assertEquals(tab, list.single())
    }

    @Test
    fun `3 - createTab triggers snapshot capture only on 0 to 1 transition`() = runTest {
        assertEquals(0, cookieManager.captureCount)

        repository.createTab("https://a.com/")
        assertEquals("first incognito tab triggers capture", 1, cookieManager.captureCount)

        repository.createTab("https://b.com/")
        repository.createTab("https://c.com/")
        assertEquals("subsequent creates do NOT re-capture", 1, cookieManager.captureCount)
    }

    @Test
    fun `4 - closeTab triggers snapshot restore only on 1 to 0 transition`() = runTest {
        val a = (repository.createTab("https://a.com/") as Result.Success).data
        val b = (repository.createTab("https://b.com/") as Result.Success).data
        assertEquals(0, cookieManager.restoreCount)

        repository.closeTab(a.id)
        assertEquals("close from 2→1 does NOT restore", 0, cookieManager.restoreCount)

        repository.closeTab(b.id)
        assertEquals("close from 1→0 triggers restore exactly once", 1, cookieManager.restoreCount)
    }

    @Test
    fun `5 - closeAll wipes state and restores snapshot`() = runTest {
        repository.createTab("https://a.com/")
        repository.createTab("https://b.com/")
        repository.createTab("https://c.com/")
        assertEquals(3, repository.getCount())

        repository.closeAll()

        assertEquals(0, repository.getCount())
        assertEquals(emptyList<Any>(), repository.observeTabs().first())
        assertEquals("closeAll restores exactly once", 1, cookieManager.restoreCount)
    }

    @Test
    fun `5b - closeAll on empty state is a no-op (no spurious restore)`() = runTest {
        assertEquals(0, repository.getCount())
        repository.closeAll()
        assertEquals(0, cookieManager.restoreCount)
    }

    @Test
    fun `6 - createTab at MAX_INCOGNITO_TABS returns Error with MaxIncognitoTabsReachedException`() = runTest {
        repeat(BrowserLimits.MAX_INCOGNITO_TABS) { i ->
            val r = repository.createTab("https://t$i.com/")
            assertTrue("seed#$i should succeed", r is Result.Success)
        }
        assertEquals(BrowserLimits.MAX_INCOGNITO_TABS, repository.getCount())

        val overflow = repository.createTab("https://overflow.com/")

        assertTrue(overflow is Result.Error)
        assertTrue((overflow as Result.Error).throwable is MaxIncognitoTabsReachedException)
        assertEquals(
            "count must remain at the cap (no row inserted)",
            BrowserLimits.MAX_INCOGNITO_TABS,
            repository.getCount(),
        )
        assertEquals(
            "snapshot captured exactly once for the whole session",
            1,
            cookieManager.captureCount,
        )
    }

    @Test
    fun `7 - IDs are unique and strictly decreasing`() = runTest {
        val ids = (0 until 5).map { i ->
            (repository.createTab("https://t$i.com/") as Result.Success).data.id
        }

        assertEquals("all ids distinct", ids.toSet().size, ids.size)
        ids.windowed(size = 2).forEach { (prev, next) ->
            assertTrue("ids should strictly decrease, got $prev → $next", next < prev)
        }
        ids.forEach { assertTrue("ids should be negative, got $it", it < 0L) }
    }

    @Test
    fun `8 - concurrent createTab and closeTab serialize via mutex (no torn state)`() = runTest {
        // Pre-seed 5 incognito tabs (well under the cap) to give close ops something to chew on.
        val seeded = (0 until 5).map { i ->
            (repository.createTab("https://seed$i.com/") as Result.Success).data
        }

        // Launch a burst of concurrent creates + closes; with the mutex in place,
        // every operation completes without exception and the final state size
        // equals (initial + creates - closes).
        val createOps = (0 until 10).map { i ->
            async(Dispatchers.Default) { repository.createTab("https://burst$i.com/") }
        }
        val closeOps = seeded.map { tab ->
            async(Dispatchers.Default) { repository.closeTab(tab.id) }
        }

        val createResults = createOps.awaitAll()
        closeOps.awaitAll()

        // All 10 creates should have succeeded (we're well under cap).
        createResults.forEach { assertTrue(it is Result.Success) }
        assertEquals(
            "5 seeded - 5 closed + 10 created = 10",
            10,
            repository.getCount(),
        )
    }

    @Test
    fun `9 - closeTab on unknown tabId is a no-op`() = runTest {
        repository.createTab("https://a.com/")
        val before = repository.getCount()

        repository.closeTab(UNKNOWN_ID)

        assertEquals(before, repository.getCount())
        assertEquals(
            "no spurious restore on unknown-id close (state did not transition 1→0)",
            0,
            cookieManager.restoreCount,
        )
    }

    @Test
    fun `10 - updateTabUrlAndTitle modifies in-memory state without touching TabRepository`() = runTest {
        val created = (repository.createTab("https://before.com/") as Result.Success).data

        repository.updateTabUrlAndTitle(created.id, "https://after.com/", "After")

        val refreshed = repository.observeTabs().first().firstOrNull { it.id == created.id }
        assertNotNull(refreshed)
        assertEquals("https://after.com/", refreshed!!.url)
        assertEquals("After", refreshed.title)

        // FakeTabRepository may have auto-seeded a home tab when its observeTabs
        // was queried for origin enumeration on 0→1 transition. That's the only
        // tab it should hold — there must be NO incognito-shaped row written
        // through to the persistence layer.
        val normalTabs = tabRepository.observeTabs().first()
        assertFalse(
            "no incognito row should have leaked into TabRepository",
            normalTabs.any { it.isIncognito },
        )
        assertFalse(
            "no after.com row should have leaked into TabRepository",
            normalTabs.any { it.url == "https://after.com/" },
        )
    }

    private companion object {
        const val UNKNOWN_ID: Long = -9_999L
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
