package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 011 — exercises each use case against [FakeTabRepository].
 *
 * Verifies that each use case correctly delegates to the repository AND
 * preserves the repository's behavioral contracts (cap-guard, last-tab
 * recreate, last-active ordering).
 */
class TabUseCasesTest {

    private lateinit var fake: FakeTabRepository
    private lateinit var observeTabs: ObserveTabsUseCase
    private lateinit var observeActiveTab: ObserveActiveTabUseCase
    private lateinit var createTab: CreateTabUseCase
    private lateinit var switchActiveTab: SwitchActiveTabUseCase
    private lateinit var closeTab: CloseTabUseCase
    private lateinit var closeAllTabs: CloseAllTabsUseCase
    private lateinit var updateTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase

    @Before
    fun setup() {
        fake = FakeTabRepository(homeUrl = HOME_URL)
        observeTabs = ObserveTabsUseCase(fake)
        observeActiveTab = ObserveActiveTabUseCase(observeTabs)
        createTab = CreateTabUseCase(fake, HOME_URL)
        switchActiveTab = SwitchActiveTabUseCase(fake)
        closeTab = CloseTabUseCase(fake)
        closeAllTabs = CloseAllTabsUseCase(fake)
        updateTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(fake)
    }

    @Test
    fun `observeActiveTab returns tab with max lastActiveAt`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 100L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 300L),
                Tab(id = 3L, url = "https://c.com", title = "", position = 2, createdAt = 0L, lastActiveAt = 200L),
            ),
        )

        val active = observeActiveTab().first()

        assertNotNull(active)
        assertEquals(2L, active!!.id)
        assertEquals("https://b.com", active.url)
    }

    @Test
    fun `createTab delegates and propagates Success`() = runTest {
        fake.reset()

        val result = createTab("https://new.com")

        assertTrue(result is Result.Success)
        assertEquals("https://new.com", (result as Result.Success).data.url)
    }

    @Test
    fun `createTab propagates MaxTabsReachedException at cap`() = runTest {
        // Pre-fill the fake to MAX_TABS via direct emission.
        val tabs = (1..BrowserLimits.MAX_TABS).map { i ->
            Tab(
                id = i.toLong(),
                url = "https://t$i.com",
                title = "",
                position = i - 1,
                createdAt = 0L,
                lastActiveAt = i.toLong(),
            )
        }
        fake.emit(tabs)

        val result = createTab("https://overflow.com")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is MaxTabsReachedException)
    }

    @Test
    fun `switchActiveTab updates lastActiveAt to make target the active tab`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 100L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 50L),
            ),
        )
        // Tab 1 is active before the switch.
        assertEquals(1L, observeActiveTab().first()!!.id)

        switchActiveTab(2L)

        assertEquals(2L, observeActiveTab().first()!!.id)
    }

    @Test
    fun `closeTab on last surviving tab triggers fresh home tab seed`() = runTest {
        fake.reset()
        fake.emit(
            listOf(
                Tab(id = 7L, url = "https://only.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )

        closeTab(7L)

        val tabs = observeTabs().first()
        assertEquals(1, tabs.size)
        assertEquals(HOME_URL, tabs.single().url)
    }

    @Test
    fun `closeAllTabs wipes then seeds exactly one fresh home tab`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 2L),
                Tab(id = 3L, url = "https://c.com", title = "", position = 2, createdAt = 0L, lastActiveAt = 3L),
            ),
        )

        closeAllTabs()

        val tabs = observeTabs().first()
        assertEquals(1, tabs.size)
        assertEquals(HOME_URL, tabs.single().url)
    }

    @Test
    fun `updateActiveTabUrlAndTitle delegates write-through`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 5L, url = "https://before.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )

        updateTabUrlAndTitle(5L, "https://after.com", "After")

        val updated = observeTabs().first().find { it.id == 5L }
        assertNotNull(updated)
        assertEquals("https://after.com", updated!!.url)
        assertEquals("After", updated.title)
    }

    @Test
    fun `updateActiveTabUrlAndTitle on missing id is a no-op`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://only.com", title = "Only", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )

        updateTabUrlAndTitle(MISSING_ID, "https://after.com", "After")

        val tabs = observeTabs().first()
        assertEquals(1, tabs.size)
        // Original row unchanged; no synthetic row appeared.
        assertEquals("https://only.com", tabs.single().url)
        assertNull(tabs.find { it.id == MISSING_ID })
    }

    private companion object {
        const val HOME_URL: String = "https://www.google.com/"
        const val MISSING_ID: Long = 999L
    }
}
