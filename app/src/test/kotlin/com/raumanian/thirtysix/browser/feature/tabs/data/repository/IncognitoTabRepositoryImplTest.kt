package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoWebProfile
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
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
 * Pure JVM: [FakeCookieJarSnapshotManager] replaces the platform seam, and [FakeTabRepository]
 * auto-seeds a home tab so origin enumeration at `0 → 1` has something to read.
 */
class IncognitoTabRepositoryImplTest {

    private val incognitoProfile = FakeIncognitoWebProfile()
    private val history = FakeHistoryRepository()
    private val fireproof = FakeFireproofSiteRepository(setOf("bank.test"))

    private lateinit var cookieManager: FakeCookieJarSnapshotManager
    private lateinit var tabRepository: FakeTabRepository
    private lateinit var repository: IncognitoTabRepositoryImpl
    private val protectionExceptions = IncognitoProtectionExceptionStore()

    @Before
    fun setup() {
        cookieManager = FakeCookieJarSnapshotManager()
        tabRepository = FakeTabRepository()
        repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = cookieManager,
            snapshotOrigins = IncognitoSnapshotOrigins(tabRepository, history, fireproof),
            dispatchers = TestDispatcherProvider(),
            incognitoProtectionExceptions = protectionExceptions,
            incognitoProfile = incognitoProfile,
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
    fun `4b - closing the last incognito tab clears incognito protection exceptions`() = runTest {
        val a = (repository.createTab("https://a.com/") as Result.Success).data
        val b = (repository.createTab("https://b.com/") as Result.Success).data
        protectionExceptions.add("a.com")

        repository.closeTab(a.id)
        assertTrue("2→1 keeps the incognito choices", protectionExceptions.contains("a.com"))

        repository.closeTab(b.id)
        assertFalse("1→0 ends them", protectionExceptions.contains("a.com"))
    }

    @Test
    fun `5c - closeAll clears incognito protection exceptions`() = runTest {
        repository.createTab("https://a.com/")
        protectionExceptions.add("a.com")

        repository.closeAll()

        assertFalse(protectionExceptions.contains("a.com"))
    }

    @Test
    fun `5 - closeAll wipes state and restores snapshot`() = runTest {
        repository.createTab("https://a.com/")
        repository.createTab("https://b.com/")
        repository.createTab("https://c.com/")
        assertEquals(3, repository.observeTabs().first().size)

        repository.closeAll()

        assertEquals(0, repository.observeTabs().first().size)
        assertEquals(emptyList<Any>(), repository.observeTabs().first())
        assertEquals("closeAll restores exactly once", 1, cookieManager.restoreCount)
    }

    @Test
    fun `5d - closeAll reports a cookie restore that could not wipe the jar`() = runTest {
        repository.createTab("https://a.com/")
        cookieManager.restoreResult = false

        assertFalse(repository.closeAll())
    }

    @Test
    fun `5b - closeAll on empty state is a no-op (no spurious restore)`() = runTest {
        assertEquals(0, repository.observeTabs().first().size)
        repository.closeAll()
        assertEquals(0, cookieManager.restoreCount)
    }

    @Test
    fun `6 - createTab at MAX_INCOGNITO_TABS returns Error with MaxIncognitoTabsReachedException`() = runTest {
        repeat(BrowserLimits.MAX_INCOGNITO_TABS) { i ->
            val r = repository.createTab("https://t$i.com/")
            assertTrue("seed#$i should succeed", r is Result.Success)
        }
        assertEquals(BrowserLimits.MAX_INCOGNITO_TABS, repository.observeTabs().first().size)

        val overflow = repository.createTab("https://overflow.com/")

        assertTrue(overflow is Result.Error)
        assertTrue((overflow as Result.Error).throwable is MaxIncognitoTabsReachedException)
        assertEquals(
            "count must remain at the cap (no row inserted)",
            BrowserLimits.MAX_INCOGNITO_TABS,
            repository.observeTabs().first().size,
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
    fun `closing the active middle tab makes the next one active`() = runTest {
        val (_, b, c) = threeTabs(activeIndex = 1)

        repository.closeTab(b)

        assertEquals(c, activeId())
    }

    @Test
    fun `closing the active last tab makes the previous one active`() = runTest {
        val (_, b, c) = threeTabs(activeIndex = 2)

        repository.closeTab(c)

        assertEquals(b, activeId())
    }

    @Test
    fun `closing a background tab leaves the active tab alone`() = runTest {
        val (a, b, _) = threeTabs(activeIndex = 0)

        repository.closeTab(b)

        assertEquals(a, activeId())
    }

    @Test
    fun `positions never repeat after a close`() = runTest {
        val (a, _, _) = threeTabs(activeIndex = 2)
        repository.closeTab(a)
        repository.createTab("https://d.com/")

        val positions = repository.observeTabs().first().map { it.position }

        assertEquals(positions.size, positions.toSet().size)
        assertEquals(listOf(1, 2, 3), positions.sorted())
    }

    @Test
    fun `closing the last incognito tab still ends the session`() = runTest {
        val id = (repository.createTab("https://only.com/") as Result.Success).data.id

        repository.closeTab(id)

        assertEquals(1, cookieManager.restoreCount)
    }

    /** Tabs opened in order; the one at [activeIndex] is switched to last, a few milliseconds later. */
    private suspend fun threeTabs(activeIndex: Int): List<Long> {
        val ids = listOf("a", "b", "c").map { name ->
            (repository.createTab("https://$name.com/") as Result.Success).data.id
        }
        Thread.sleep(STAMP_GAP_MS)
        repository.switchActiveTab(ids[activeIndex])
        return ids
    }

    private suspend fun activeId(): Long = repository.observeTabs().first().first().id

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
            repository.observeTabs().first().size,
        )
    }

    @Test
    fun `9 - closeTab on unknown tabId is a no-op`() = runTest {
        repository.createTab("https://a.com/")
        val before = repository.observeTabs().first().size

        repository.closeTab(UNKNOWN_ID)

        assertEquals(before, repository.observeTabs().first().size)
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

    @Test
    fun `11 - a tab on the Home page contributes no cookie origin`() = runTest {
        // The reserved Home address has a scheme but no host,
        // so the snapshot captures only the real page's origin.
        val withHome = FakeTabRepository(
            initialTabs = listOf(
                com.raumanian.thirtysix.browser.core.domain.model.Tab(1, UrlConstants.HOME_PAGE_URL, "", 0, 0L, 2L),
                com.raumanian.thirtysix.browser.core.domain.model.Tab(2, "https://real.example/", "R", 1, 0L, 1L),
            ),
        )
        val repo = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = cookieManager,
            snapshotOrigins = IncognitoSnapshotOrigins(
                withHome,
                FakeHistoryRepository(),
                FakeFireproofSiteRepository(),
            ),
            dispatchers = TestDispatcherProvider(),
            incognitoProtectionExceptions = protectionExceptions,
            incognitoProfile = incognitoProfile,
        )

        repo.createTab(UrlConstants.HOME_PAGE_URL)

        assertEquals(listOf("https://real.example"), cookieManager.lastCapturedOrigins)
    }

    private companion object {
        const val UNKNOWN_ID: Long = -9_999L
        const val STAMP_GAP_MS: Long = 5L
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    @Test
    fun `the snapshot keeps every site in history and every fireproof site, not only open tabs`() = runTest {
        history.recordVisit("https://mail.example.com/inbox", "", 1L)
        history.recordVisit("http://old.test/page", "", 2L)

        repository.createTab("https://a.com/")

        val origins = cookieManager.lastCapturedOrigins
        assertTrue(origins.containsAll(listOf("https://mail.example.com", "https://old.test", "http://old.test")))
        assertTrue(origins.containsAll(listOf("https://bank.test", "https://www.bank.test")))
    }

    @Test
    fun `with profile isolation a session begins and ends its own profile and never touches the jar`() = runTest {
        incognitoProfile.isolated = true
        val a = (repository.createTab("https://a.com/") as Result.Success).data
        val b = (repository.createTab("https://b.com/") as Result.Success).data
        assertEquals(1, incognitoProfile.begun)

        repository.closeTab(a.id)
        assertEquals("2 → 1 keeps the session", 0, incognitoProfile.ended)
        repository.closeTab(b.id)

        assertEquals(1, incognitoProfile.ended)
        assertEquals("no snapshot taken", 0, cookieManager.captureCount)
        assertEquals("normal cookies are never wiped", 0, cookieManager.restoreCount)
    }

    @Test
    fun `closeAll ends an isolated session and clears incognito protection exceptions`() = runTest {
        incognitoProfile.isolated = true
        repository.createTab("https://a.com/")
        protectionExceptions.add("a.com")

        repository.closeAll()

        assertEquals(1, incognitoProfile.ended)
        assertEquals(0, cookieManager.restoreCount)
        assertTrue(protectionExceptions.snapshot().isEmpty())
    }

    @Test
    fun `a session keeps the path it began with even if detection changes`() = runTest {
        val a = (repository.createTab("https://a.com/") as Result.Success).data
        incognitoProfile.isolated = true

        repository.closeTab(a.id)

        assertEquals("began on the snapshot path, so it restores", 1, cookieManager.restoreCount)
        assertEquals(0, incognitoProfile.ended)
    }
}
