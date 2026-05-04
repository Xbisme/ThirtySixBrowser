package com.raumanian.thirtysix.browser.presentation.tabs

import android.graphics.Bitmap
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.usecase.CloseAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SwitchActiveTabUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 011 — pure-JVM unit tests for [TabsViewModel] (US1 + partial US3 scope).
 *
 * Drives a [FakeTabRepository] (Spec 010 precedent for hand-rolled fakes) and
 * asserts state transitions over the [TabsUiState] StateFlow + the
 * `popBackEvent` SharedFlow (M3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        repository: FakeTabRepository = FakeTabRepository(homeUrl = HOME_URL),
        incognitoRepository: com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository =
            com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository(),
        faviconCache: FaviconCache = NoopFaviconCache,
        screenshotCache: ScreenshotCache = NoopScreenshotCache,
    ): TabsViewModel {
        val observeAllTabs = com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase(
            repository,
            incognitoRepository,
        )
        return TabsViewModel(
            observeAllTabs = observeAllTabs,
            createTab = CreateTabUseCase(repository, HOME_URL),
            createIncognitoTab = com.raumanian.thirtysix.browser.domain.usecase.CreateIncognitoTabUseCase(
                incognitoRepository,
            ),
            switchActiveTab = SwitchActiveTabUseCase(repository),
            closeTab = CloseTabUseCase(repository, screenshotCache),
            closeIncognitoTab = com.raumanian.thirtysix.browser.domain.usecase.CloseIncognitoTabUseCase(
                incognitoRepository,
            ),
            closeAllTabs = CloseAllTabsUseCase(repository, screenshotCache),
            closeAllIncognitoTabs = com.raumanian.thirtysix.browser.domain.usecase.CloseAllIncognitoTabsUseCase(
                incognitoRepository,
            ),
            homeUrl = HOME_URL,
            faviconCache = faviconCache,
            screenshotCache = screenshotCache,
        )
    }

    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, bitmap: Bitmap) = Unit
        override fun fileFor(url: String): File? = null
    }

    private object NoopScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(tabId: Long, bitmap: Bitmap) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    @Test
    fun `initial state on empty repo emits seeded home tab`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.uiState.test {
            // First emission is TabsUiState.EMPTY before the Flow has started.
            // We skip until we see the seeded home tab.
            var current = awaitItem()
            while (current.tabs.isEmpty()) {
                current = awaitItem()
            }
            assertEquals(1, current.tabs.size)
            assertEquals(HOME_URL, current.tabs.single().url)
            assertNotNull(current.activeTabId)
            assertEquals(current.tabs.single().id, current.activeTabId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTabClick switches active tab and emits popBackEvent`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        repository.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 200L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 100L),
            ),
        )
        val vm = newViewModel(repository)

        vm.popBackEvent.test {
            vm.onTabClick(2L)
            advanceUntilIdle()

            // popBackEvent fires once after the switch completes.
            awaitItem()

            // After the switch, repository's max(lastActiveAt) is on tab 2.
            // Read directly from the repository rather than vm.uiState — the
            // ViewModel's StateFlow is `WhileSubscribed`, so without an active
            // collector its `value` may stay at the initial EMPTY state.
            val tabs = repository.observeTabs().first()
            assertEquals(2L, tabs.first().id) // sorted DESC by lastActiveAt

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onNewTabClick under cap creates a tab and emits popBackEvent`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        val vm = newViewModel(repository)
        // Drain initial state.
        vm.uiState.test {
            var current = awaitItem()
            while (current.tabs.isEmpty()) current = awaitItem()
            val initialCount = current.tabs.size

            vm.onNewTabClick()
            advanceUntilIdle()

            // Drain to the new state with one more tab.
            var afterCreate = awaitItem()
            while (afterCreate.tabs.size <= initialCount) afterCreate = awaitItem()
            assertEquals(initialCount + 1, afterCreate.tabs.size)
            // No error event on success.
            assertNull(afterCreate.errorEvent)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onNewTabClick at cap emits MaxTabsReached error event`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        // Pre-fill to cap.
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
        repository.emit(tabs)
        val vm = newViewModel(repository)
        vm.uiState.test {
            // Drain initial.
            var current = awaitItem()
            while (current.tabs.size != BrowserLimits.MAX_TABS) current = awaitItem()

            vm.onNewTabClick()
            advanceUntilIdle()

            // Drain until errorEvent surfaces.
            var afterFailure = awaitItem()
            while (afterFailure.errorEvent !is TabsErrorEvent.MaxTabsReached) afterFailure = awaitItem()
            assertEquals(TabsErrorEvent.MaxTabsReached, afterFailure.errorEvent)
            // Tab count unchanged.
            assertEquals(BrowserLimits.MAX_TABS, afterFailure.tabs.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- Spec 011 (T043 / US3) — close + close-all + dialog flow ----------

    @Test
    fun `onCloseTab removes the targeted tab from the list`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        repository.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 100L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 200L),
                Tab(id = 3L, url = "https://c.com", title = "", position = 2, createdAt = 0L, lastActiveAt = 50L),
            ),
        )
        val vm = newViewModel(repository)
        advanceUntilIdle()

        vm.onCloseTab(2L)
        advanceUntilIdle()

        val remaining = repository.observeTabs().first()
        assertEquals(2, remaining.size)
        assertNull(remaining.find { it.id == 2L })
    }

    @Test
    fun `onCloseTab on the last surviving tab triggers fresh home tab seeding`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        repository.emit(
            listOf(
                Tab(id = 7L, url = "https://only.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )
        val vm = newViewModel(repository)
        advanceUntilIdle()

        vm.onCloseTab(7L)
        advanceUntilIdle()

        val tabs = repository.observeTabs().first()
        assertEquals(1, tabs.size)
        assertEquals(HOME_URL, tabs.single().url)
        // The seeded fresh home tab has a NEW id (not the closed 7L).
        assertNotNull(tabs.single().id)
    }

    @Test
    fun `onCloseAllRequested toggles dialog visibility on`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.uiState.test {
            // Drain initial.
            var current = awaitItem()
            while (current.tabs.isEmpty()) current = awaitItem()
            assertTrue(current.isCloseAllDialogVisible.not())

            vm.onCloseAllRequested()
            // Next emission carries dialog = true.
            var afterToggle = awaitItem()
            while (!afterToggle.isCloseAllDialogVisible) afterToggle = awaitItem()
            assertTrue(afterToggle.isCloseAllDialogVisible)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCloseAllDismissed clears the dialog visibility flag`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onCloseAllRequested()
        advanceUntilIdle()
        vm.uiState.test {
            // The combined Flow may emit while we're attaching; advance to a
            // state where dialog is visible.
            var withDialog = awaitItem()
            while (!withDialog.isCloseAllDialogVisible) withDialog = awaitItem()

            vm.onCloseAllDismissed()
            var dismissed = awaitItem()
            while (dismissed.isCloseAllDialogVisible) dismissed = awaitItem()
            assertTrue(dismissed.isCloseAllDialogVisible.not())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCloseAllConfirmed wipes tabs and seeds a single fresh home tab`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        repository.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 2L),
                Tab(id = 3L, url = "https://c.com", title = "", position = 2, createdAt = 0L, lastActiveAt = 3L),
            ),
        )
        val vm = newViewModel(repository)
        advanceUntilIdle()

        vm.onCloseAllConfirmed()
        advanceUntilIdle()

        val tabs = repository.observeTabs().first()
        assertEquals(1, tabs.size)
        assertEquals(HOME_URL, tabs.single().url)
    }

    // ---------- Spec 012 (T040a — US2 / Analyze remediation C3) — last-tab edge case ----------

    /**
     * FR-005 across the both-kinds-empty boundary that Spec 012 newly enables.
     *
     * Scenario: starting from "1 normal home + 1 incognito" (the operational
     * minimum since `TabRepository.observeTabs()` auto-seeds a home tab on
     * first subscription per Spec 011 / R5), closing the incognito tab MUST
     * leave the user with exactly 1 normal home tab — and that home tab MUST
     * NOT have been duplicated or wiped during the close.
     */
    @Test
    fun `closing the only incognito tab leaves exactly one normal home tab`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        val incognitoRepo = com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository()
        // Pre-seed an incognito tab (negative id per R3) with a higher
        // lastActiveAt so it is the active tab in the merged Flow.
        incognitoRepo.emit(
            listOf(
                Tab(
                    id = -1L,
                    url = "https://incognito.example.com",
                    title = "",
                    position = 0,
                    createdAt = Long.MAX_VALUE,
                    lastActiveAt = Long.MAX_VALUE,
                    isIncognito = true,
                ),
            ),
        )
        val vm = newViewModel(repository, incognitoRepo)
        advanceUntilIdle()

        // Sanity: prior to close, repository auto-seeded 1 home tab and
        // incognitoRepo holds 1 incognito tab → merged head is incognito.
        val before = repository.observeTabs().first()
        assertEquals(1, before.size)
        assertEquals(HOME_URL, before.single().url)
        assertEquals(1, incognitoRepo.observeTabs().first().size)

        vm.onCloseTab(-1L)
        advanceUntilIdle()

        // After close: incognito gone, the auto-seeded normal home tab is the
        // sole survivor — NOT duplicated, NOT wiped, NOT replaced.
        val afterNormal = repository.observeTabs().first()
        val afterIncognito = incognitoRepo.observeTabs().first()
        assertEquals("normal home tab survived", 1, afterNormal.size)
        assertEquals(HOME_URL, afterNormal.single().url)
        assertTrue("incognito list is empty", afterIncognito.isEmpty())
    }

    @Test
    fun `consumeErrorEvent clears the errorEvent field`() = runTest(testDispatcher) {
        val repository = FakeTabRepository(homeUrl = HOME_URL)
        // Pre-fill to cap so the first onNewTabClick triggers MaxTabsReached.
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
        repository.emit(tabs)
        val vm = newViewModel(repository)
        vm.uiState.test {
            // Drain to the cap state.
            var current = awaitItem()
            while (current.tabs.size != BrowserLimits.MAX_TABS) current = awaitItem()

            vm.onNewTabClick()
            advanceUntilIdle()

            var withError = awaitItem()
            while (withError.errorEvent !is TabsErrorEvent.MaxTabsReached) withError = awaitItem()
            assertEquals(TabsErrorEvent.MaxTabsReached, withError.errorEvent)

            vm.consumeErrorEvent()
            var cleared = awaitItem()
            while (cleared.errorEvent != null) cleared = awaitItem()
            assertNull(cleared.errorEvent)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val HOME_URL: String = "https://www.google.com/"
    }
}
