package com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingTarget
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.OpenOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 020 T020 — every `OpenIncomingRequestUseCase` row of contracts C2
 * (FR-003, FR-007, FR-017, FR-018; INV-1, INV-5, INV-6).
 */
class OpenIncomingRequestUseCaseTest {

    private var now = 10_000L
    private val clock = MonotonicClock { now }
    private val incognito = FakeIncognitoTabRepository()

    private object TestSearchEngine : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String = "https://search.test/?q=$query"
    }

    private fun useCase(tabs: TabRepository) = OpenIncomingRequestUseCase(
        buildSearchUrl = BuildSearchUrlUseCase(TestSearchEngine),
        tabs = tabs,
        observeActiveTab = ObserveActiveTabUseCase(ObserveAllTabsUseCase(tabs, incognito)),
        clock = clock,
    )

    private fun tab(id: Long, lastActiveAt: Long) = Tab(
        id = id,
        url = "https://t$id.test/",
        title = "",
        position = id.toInt(),
        createdAt = 0L,
        lastActiveAt = lastActiveAt,
    )

    @Test
    fun `a load under the cap opens a new active tab`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val outcome = useCase(tabs)(IncomingTarget.Load("https://example.org/a"))

        assertTrue(outcome is OpenOutcome.OpenedInNewTab)
        val all = tabs.observeTabs().first()
        assertEquals(2, all.size)
        assertEquals("https://example.org/a", all.single { it.id == (outcome as OpenOutcome.OpenedInNewTab).tabId }.url)
        // The use case has no path to the incognito store at all (INV-1); it stays empty.
        assertEquals(0, incognito.getCount())
    }

    @Test
    fun `a search builds the URL with the chosen engine`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val outcome = useCase(tabs)(IncomingTarget.Search("pho"))

        val id = (outcome as OpenOutcome.OpenedInNewTab).tabId
        assertEquals("https://search.test/?q=pho", tabs.observeTabs().first().single { it.id == id }.url)
    }

    @Test
    fun `the same URL inside the window is a duplicate`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val open = useCase(tabs)
        open(IncomingTarget.Load("https://example.org/a"))
        now += BrowserLimits.INCOMING_DUPLICATE_WINDOW_MS

        assertEquals(OpenOutcome.Duplicate, open(IncomingTarget.Load("https://example.org/a")))
        assertEquals(2, tabs.observeTabs().first().size)
    }

    @Test
    fun `the same URL just after the window opens again`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val open = useCase(tabs)
        open(IncomingTarget.Load("https://example.org/a"))
        now += BrowserLimits.INCOMING_DUPLICATE_WINDOW_MS + 1

        assertTrue(open(IncomingTarget.Load("https://example.org/a")) is OpenOutcome.OpenedInNewTab)
    }

    @Test
    fun `a different URL inside the window opens`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val open = useCase(tabs)
        open(IncomingTarget.Load("https://example.org/a"))

        assertTrue(open(IncomingTarget.Load("https://example.org/b")) is OpenOutcome.OpenedInNewTab)
    }

    @Test
    fun `at the cap the most recently used tab is reused and no tab is created`() = runTest {
        val full = (1L..BrowserLimits.MAX_TABS).map { id -> tab(id, lastActiveAt = id) }
        // Tab 7 is the most recently used one.
        val roster = full.map { if (it.id == 7L) it.copy(lastActiveAt = 1_000L) else it }
        val tabs = FakeTabRepository(initialTabs = roster)

        val outcome = useCase(tabs)(IncomingTarget.Load("https://example.org/a"))

        // Tab 7 was already active: the caller loads the URL into the live page.
        assertEquals(OpenOutcome.OpenedInExistingTab(7L, "https://example.org/a", loadInPlace = true), outcome)
        assertEquals(BrowserLimits.MAX_TABS, tabs.observeTabs().first().size)
        assertEquals("https://t7.test/", tabs.observeTabs().first().single { it.id == 7L }.url)
    }

    @Test
    fun `at the cap with an incognito tab active the normal tab gets the address before the switch`() = runTest {
        val roster = (1L..BrowserLimits.MAX_TABS).map { id -> tab(id, lastActiveAt = id) }
            .map { if (it.id == 7L) it.copy(lastActiveAt = 1_000L) else it }
        val tabs = FakeTabRepository(initialTabs = roster)
        incognito.createTab("https://private.test/") // newest lastActiveAt → the incognito tab is active

        val outcome = useCase(tabs)(IncomingTarget.Load("https://example.org/a"))

        assertEquals(OpenOutcome.OpenedInExistingTab(7L, "https://example.org/a", loadInPlace = false), outcome)
        assertEquals("https://example.org/a", tabs.observeTabs().first().single { it.id == 7L }.url)
        assertEquals(1, incognito.getCount())
    }

    @Test
    fun `a store error other than the cap fails`() = runTest {
        val broken = object : TabRepository by FakeTabRepository() {
            override suspend fun createTab(url: String): Result<Tab> =
                Result.Error(throwable = IllegalStateException("disk"))
            override fun observeTabs(): Flow<List<Tab>> = FakeTabRepository().observeTabs()
        }
        assertEquals(OpenOutcome.Failed, useCase(broken)(IncomingTarget.Load("https://example.org/a")))
    }
}
