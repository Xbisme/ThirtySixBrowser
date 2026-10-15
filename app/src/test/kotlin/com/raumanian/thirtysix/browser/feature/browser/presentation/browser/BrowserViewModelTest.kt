package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import java.io.File
import java.io.OutputStream
import java.net.URLEncoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * JVM tests for [BrowserViewModel]. Query submits run in `viewModelScope`, so those tests advance
 * the dispatcher; [GoogleByteIdenticalRepository] pins the exact Google encoding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Suppress("LongParameterList")
    private fun newViewModel(
        url: String = SAMPLE_PAGE_URL,
        searchEngine: SearchEngineRepository = GoogleByteIdenticalRepository,
        tabRepository: FakeTabRepository = FakeTabRepository(homeUrl = url),
        faviconCache: FaviconCache = NoopFaviconCache,
        screenshotCache: ScreenshotCache = NoopScreenshotCache,
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
    ): BrowserViewModel {
        // Tab use cases and caches share one self-contained fake by default.
        val incognitoRepo = FakeIncognitoTabRepository()
        val observeAllTabs = ObserveAllTabsUseCase(tabRepository, incognitoRepo)
        val observeActiveTab = ObserveActiveTabUseCase(observeAllTabs)
        val observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs)
        return BrowserViewModel(
            searchEngine = searchEngine,
            observeActiveTab = observeActiveTab,
            tabs = tabRepository,
            observeActiveTabIsIncognito = observeActiveTabIsIncognito,
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepository, incognitoRepo),
            faviconCache = faviconCache,
            screenshotCache = screenshotCache,
            recordHistoryEntry = RecordHistoryEntryUseCase(historyRepository),
            updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(historyRepository),
            pageStore = CurrentPageRepositoryImpl(url),
        )
    }

    /** No-op [FaviconCache] for tests. */
    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun retainOnly(hosts: Set<String>) = Unit
        override fun fileFor(url: String): File? = null
    }

    /** No-op [ScreenshotCache] for tests. */
    private object NoopScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    /** Reproduces `URLEncoder.encode(query, "UTF-8")` into the Google template, byte for byte. */
    private object GoogleByteIdenticalRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            return String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
        }
    }

    @Test
    fun `initial state matches injected URL and Idle loading`() = runTest {
        val vm = newViewModel("https://example.com")
        vm.uiState.test {
            val first = awaitItem()
            assertEquals("https://example.com", vm.currentPage.value.url)
            assertEquals(LoadingState.Idle, first.loadingState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `happy path Idle to Loading to Loaded via progress`() = runTest {
        val vm = newViewModel()
        // Production fires onUrlChange before onLoadStarted (see `onPageStarted`); mirror that order.
        vm.onAction(BrowserAction.Page.UrlChanged("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(EARLY_PROGRESS))
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS))
        vm.onAction(BrowserAction.Page.ProgressChanged(MAX_PROGRESS))

        val state = vm.screenState()
        assertEquals("https://example.com", state.currentUrl)
        assertEquals(LoadingState.Loaded, state.loadingState)
    }

    @Test
    fun `loaded via onPageFinished without final progress event`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS)) // never reaches 100
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))

        val state = vm.screenState()
        assertEquals(LoadingState.Loaded, state.loadingState)
    }

    @Test
    fun `onLoadFinished is idempotent`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
        val firstLoaded = vm.screenState()
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com")) // second call
        val secondLoaded = vm.screenState()

        assertEquals(firstLoaded, secondLoaded)
        assertEquals(LoadingState.Loaded, secondLoaded.loadingState)
    }

    @Test
    fun `progress over 100 is clamped to Loaded`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(OVER_PROGRESS)) // defensive: 150
        assertEquals(LoadingState.Loaded, vm.screenState().loadingState)
    }

    @Test
    fun `progress under 0 is clamped to Loading 0f`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(NEGATIVE_PROGRESS)) // defensive: -5
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Loading)
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    @Test
    fun `transitions to Failed on onLoadFailed during loading`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.NetworkUnavailable))
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Failed)
        assertEquals(ErrorReason.NetworkUnavailable, (state.loadingState as LoadingState.Failed).reason)
    }

    @Test
    fun `Loaded then onLoadStarted again then onLoadFailed lands on Failed`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadStarted("https://other.example.com")) // simulating a reload
        vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.SslError))
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Failed)
        assertEquals(ErrorReason.SslError, (state.loadingState as LoadingState.Failed).reason)
    }

    @Test
    fun `recovery from Failed via onLoadStarted re-enters Loading 0f`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.DnsFailure))
        // Now retry
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Loading)
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    @Test
    fun `progress integer maps to expected Float in 0_to_1f range`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(0))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
        vm.onAction(BrowserAction.Page.ProgressChanged(EARLY_PROGRESS)) // 25
        assertEquals(0.25f, vm.loadProgress.value, FLOAT_TOLERANCE)
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS)) // 50
        assertEquals(0.5f, vm.loadProgress.value, FLOAT_TOLERANCE)
        vm.onAction(BrowserAction.Page.ProgressChanged(NEAR_PROGRESS)) // 99
        assertEquals(0.99f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    // ---------- canGoBack / canGoForward ----------

    @Test
    fun `initial canGoBack and canGoForward are false`() = runTest {
        val vm = newViewModel()
        val state = vm.screenState()
        assertEquals(false, state.canGoBack)
        assertEquals(false, state.canGoForward)
    }

    @Test
    fun `onCanGoBackChanged true propagates to state`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.CanGoBackChanged(true))
        assertEquals(true, vm.screenState().canGoBack)
        assertEquals(false, vm.screenState().canGoForward) // independent
    }

    @Test
    fun `onCanGoBackChanged false reverts to false`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.CanGoBackChanged(true))
        vm.onAction(BrowserAction.Page.CanGoBackChanged(false))
        assertEquals(false, vm.screenState().canGoBack)
    }

    @Test
    fun `onCanGoForwardChanged true propagates to state`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.CanGoForwardChanged(true))
        assertEquals(true, vm.screenState().canGoForward)
        assertEquals(false, vm.screenState().canGoBack) // independent
    }

    @Test
    fun `onCanGoBackChanged and onCanGoForwardChanged are independent`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.CanGoBackChanged(true))
        vm.onAction(BrowserAction.Page.CanGoForwardChanged(true))
        val state = vm.screenState()
        assertEquals(true, state.canGoBack)
        assertEquals(true, state.canGoForward)

        vm.onAction(BrowserAction.Page.CanGoBackChanged(false))
        val after = vm.screenState()
        assertEquals(false, after.canGoBack)
        assertEquals(true, after.canGoForward) // forward retains its value
    }

    // ---------- onLoadStopped ----------

    @Test
    fun `onLoadStopped during Loading transitions to Loaded`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS)) // partial load
        assertTrue(vm.screenState().loadingState is LoadingState.Loading)

        vm.onAction(BrowserAction.Page.LoadStopped)
        assertEquals(LoadingState.Loaded, vm.screenState().loadingState)
    }

    @Test
    fun `onLoadStopped from non-Loading state is no-op`() = runTest {
        val vm = newViewModel()
        // From Idle
        vm.onAction(BrowserAction.Page.LoadStopped)
        assertEquals(LoadingState.Idle, vm.screenState().loadingState)

        // From Loaded
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
        val loaded = vm.screenState()
        vm.onAction(BrowserAction.Page.LoadStopped)
        assertEquals(loaded, vm.screenState())

        // From Failed
        vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.NetworkUnavailable))
        val failed = vm.screenState()
        vm.onAction(BrowserAction.Page.LoadStopped)
        assertEquals(failed, vm.screenState())
    }

    // ---------- Address bar defaults ----------

    @Test
    fun `initial address-bar text is empty and not focused`() = runTest {
        val vm = newViewModel()
        val state = vm.screenState()
        assertEquals("", state.addressBarText)
        assertEquals(false, state.isAddressBarFocused)
    }

    // ---------- Address bar text, focus and submit ----------

    @Test
    fun `onAddressBarTextChange updates state`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("foo"))
        assertEquals("foo", vm.screenState().addressBarText)
    }

    @Test
    fun `onAddressBarFocusChange toggles flag`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.FocusChange(true))
        assertEquals(true, vm.screenState().isAddressBarFocused)
        vm.onAction(BrowserAction.AddressBar.FocusChange(false))
        assertEquals(false, vm.screenState().isAddressBarFocused)
    }

    @Test
    fun `onAddressBarSubmit returns false on empty input and does not call loadUrl`() = runTest {
        val vm = newViewModel()
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        assertTrue(calls.loads().isEmpty())
    }

    @Test
    fun `onAddressBarSubmit returns false on whitespace-only input`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("   "))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        assertTrue(calls.loads().isEmpty())
    }

    @Test
    fun `onAddressBarSubmit URL input prepends https and calls loadUrl`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("example.com"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        assertEquals(listOf("https://example.com"), calls.loads())
    }

    @Test
    fun `onAddressBarSubmit explicit http scheme is preserved`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("http://example.com"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        assertEquals(listOf("http://example.com"), calls.loads())
    }

    /**
     * Two consecutive submits forward both URLs in order: the WebView cancels the first load,
     * so the view model must not coalesce or skip submissions.
     */
    @Test
    fun `two consecutive submits forward both URLs to loadUrl in order`() = runTest {
        val vm = newViewModel()
        val calls = eventsOf(vm)

        vm.onAction(BrowserAction.AddressBar.TextChange("first.com"))
        vm.onAction(BrowserAction.AddressBar.Submit)
        vm.onAction(BrowserAction.AddressBar.TextChange("second.com"))
        vm.onAction(BrowserAction.AddressBar.Submit)

        assertEquals(listOf("https://first.com", "https://second.com"), calls.loads())
    }

    // ---------- Address bar query path ----------

    @Test
    fun `onAddressBarSubmit query input builds Google search URL`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("kotlin coroutines"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        advanceUntilIdle()
        // URLEncoder.encode renders space as `+` in form-urlencoded mode.
        assertEquals(listOf("https://www.google.com/search?q=kotlin+coroutines"), calls.loads())
    }

    @Test
    fun `onAddressBarSubmit query encodes special characters`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("c++ vs rust"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        advanceUntilIdle()
        // `+` becomes `%2B`; space becomes `+`.
        assertEquals(listOf("https://www.google.com/search?q=c%2B%2B+vs+rust"), calls.loads())
    }

    @Test
    fun `onAddressBarSubmit query encodes Unicode`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("안녕"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        advanceUntilIdle()
        // Korean "annyeong" UTF-8 percent-encoded.
        assertEquals(listOf("https://www.google.com/search?q=%EC%95%88%EB%85%95"), calls.loads())
    }

    // ---------- Query path through BuildSearchUrlUseCase ----------

    @Test
    fun `query branch dispatches to BuildSearchUrlUseCase`() = runTest(testDispatcher) {
        var receivedQuery: String? = null
        val recordingRepo = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                receivedQuery = query
                return "https://example.test/search?q=$query"
            }
        }
        val vm = newViewModel(searchEngine = recordingRepo)
        vm.onAction(BrowserAction.AddressBar.TextChange("kotlin"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        advanceUntilIdle()

        assertEquals("kotlin", receivedQuery)
        assertEquals(listOf("https://example.test/search?q=kotlin"), calls.loads())
    }

    @Test
    fun `URL branch does not invoke BuildSearchUrlUseCase`() = runTest(testDispatcher) {
        var useCaseCalled = false
        val watcher = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                useCaseCalled = true
                return "should-not-be-used"
            }
        }
        val vm = newViewModel(searchEngine = watcher)
        vm.onAction(BrowserAction.AddressBar.TextChange("example.com"))
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        advanceUntilIdle()

        assertEquals(false, useCaseCalled)
        assertEquals(listOf("https://example.com"), calls.loads())
    }

    @Test
    fun `empty submit returns false and never invokes BuildSearchUrlUseCase`() = runTest(testDispatcher) {
        var useCaseCalled = false
        val watcher = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                useCaseCalled = true
                return "irrelevant"
            }
        }
        val vm = newViewModel(searchEngine = watcher)
        // No onAddressBarTextChange call → addressBarText is empty.
        val calls = eventsOf(vm)
        vm.onAction(BrowserAction.AddressBar.Submit)
        advanceUntilIdle()

        assertEquals(false, useCaseCalled)
        assertTrue(calls.loads().isEmpty())
    }

    // ---------- onUrlChanged and onLoadStarted ----------

    @Test
    fun `onUrlChanged updates currentUrl and does not affect addressBarText or focus`() = runTest {
        val vm = newViewModel("https://example.com")
        vm.onAction(BrowserAction.AddressBar.TextChange("typing-in-progress"))
        vm.onAction(BrowserAction.AddressBar.FocusChange(true))
        vm.onAction(BrowserAction.Page.UrlChanged("https://example.com/about"))
        val state = vm.screenState()
        assertEquals("https://example.com/about", state.currentUrl)
        assertEquals("typing-in-progress", state.addressBarText)
        assertEquals(true, state.isAddressBarFocused)
    }

    /** `onLoadStarted` only flips the loading state; [onUrlChanged] owns `currentUrl`. */
    @Test
    fun `onLoadStarted does not mutate currentUrl on load start`() = runTest {
        val vm = newViewModel("https://initial.example.com")
        vm.onAction(BrowserAction.Page.LoadStarted("https://other.example.com"))
        val state = vm.screenState()
        assertEquals("https://initial.example.com", state.currentUrl)
        assertTrue(state.loadingState is LoadingState.Loading)
    }

    /**
     * After submit the WebView's `onPageStarted` fires synchronously; the stub `loadUrl` mirrors it,
     * so `currentUrl` shows the target before any `onLoadFinished`.
     */
    @Test
    fun `submit triggers immediate currentUrl update via onUrlChanged`() = runTest {
        val vm = newViewModel("https://before.example.com")
        // The screen's load mirrors the real WebView's onPageStarted → onUrlChanged chain.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { event ->
                if (event is BrowserEvent.LoadUrl) vm.onAction(BrowserAction.Page.UrlChanged(event.url))
            }
        }

        vm.onAction(BrowserAction.AddressBar.TextChange("example.com"))
        vm.onAction(BrowserAction.AddressBar.Submit)

        assertEquals("https://example.com", vm.screenState().currentUrl)
    }

    // ---------- onAddressBarClear ----------

    @Test
    fun `onAddressBarClear empties text and preserves focus`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.AddressBar.TextChange("partial input"))
        vm.onAction(BrowserAction.AddressBar.FocusChange(true))
        vm.onAction(BrowserAction.AddressBar.Clear)
        val state = vm.screenState()
        assertEquals("", state.addressBarText)
        assertEquals(true, state.isAddressBarFocused)
    }

    // ---------- Active-tab seeding and write-through ----------

    @Test
    fun `init seeds currentUrl from the active tab once Flow emits`() = runTest(testDispatcher) {
        // FakeTabRepository auto-seeds an empty list with one home tab on first
        // observeTabs() subscription. After advanceUntilIdle the seeded URL
        // should override the constructor's defaultHomeUrl.
        val seededUrl = "https://www.google.com/"
        val vm = newViewModel(url = seededUrl)
        advanceUntilIdle()
        assertEquals(seededUrl, vm.screenState().currentUrl)
    }

    // ---------- History recording ----------

    @Test
    fun `onLoadFinished records one history entry on a fresh load`() = runTest(testDispatcher) {
        val historyRepo = FakeHistoryRepository()
        val vm = newViewModel(historyRepository = historyRepo)
        advanceUntilIdle()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.TitleReceived("Example"))
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
        advanceUntilIdle()
        assertEquals(1, historyRepo.recorded.size)
        val entry = historyRepo.recorded.first()
        assertEquals("https://example.com", entry.url)
        assertEquals("Example", entry.title)
    }

    @Test
    fun `progress 100 plus onLoadFinished records exactly one entry (no double)`() =
        runTest(testDispatcher) {
            val historyRepo = FakeHistoryRepository()
            val vm = newViewModel(historyRepository = historyRepo)
            advanceUntilIdle()
            vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
            vm.onAction(BrowserAction.Page.TitleReceived("Example"))
            vm.onAction(BrowserAction.Page.ProgressChanged(MAX_PROGRESS)) // marks state Loaded
            // Idempotent on Loaded + same URL: no record.
            vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
            advanceUntilIdle()
            assertEquals(1, historyRepo.recorded.size)
        }

    @Test
    fun `repeat onLoadFinished after reload produces a second entry`() =
        runTest(testDispatcher) {
            val historyRepo = FakeHistoryRepository()
            val vm = newViewModel(historyRepository = historyRepo)
            advanceUntilIdle()
            // First load.
            vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
            vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
            advanceUntilIdle()
            // Reload (state transitions from Loaded → Loading → Loaded).
            vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
            vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
            advanceUntilIdle()
            assertEquals(2, historyRepo.recorded.size)
        }

    @Test
    fun `onLoadFailed never invokes the recorder`() =
        runTest(testDispatcher) {
            val historyRepo = FakeHistoryRepository()
            val vm = newViewModel(historyRepository = historyRepo)
            advanceUntilIdle()
            vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
            vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.NetworkUnavailable))
            advanceUntilIdle()
            assertEquals(0, historyRepo.recorded.size)
        }

    // ---------- Progress kept out of uiState ----------

    @Test
    fun `fifty progress ticks emit fifty progress values and no uiState`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(1)) // enters Loading once; isolation is about what follows
        vm.uiState.test {
            awaitItem()
            val seen = mutableListOf<Float>()
            vm.loadProgress.test {
                awaitItem()
                for (tick in 2..ISOLATION_TICKS + 1) {
                    vm.onAction(BrowserAction.Page.ProgressChanged(tick))
                    seen += awaitItem()
                }
            }
            assertEquals(ISOLATION_TICKS, seen.size)
            expectNoEvents()
        }
    }

    @Test
    fun `progress resets to zero on start, finish, failure and stop`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS))
        vm.onAction(BrowserAction.Page.LoadFinished("https://example.com"))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)

        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS))
        vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.NetworkUnavailable))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)

        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS))
        vm.onAction(BrowserAction.Page.LoadStopped)
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)

        vm.onAction(BrowserAction.Page.ProgressChanged(MID_PROGRESS))
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com"))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    // ---------- Home inside a tab ----------

    @Test
    fun `a tab at the Home address shows Home content`() = runTest {
        val vm = newViewModel(UrlConstants.HOME_PAGE_URL)
        advanceUntilIdle()
        assertTrue(vm.screenState().isHomeContent)
    }

    @Test
    fun `Home pressed on a page overlays Home and Back returns to the page`() = runTest {
        val vm = newViewModel("https://page.test/")
        advanceUntilIdle()
        vm.onAction(BrowserAction.Home.Pressed)
        assertTrue(vm.screenState().isHomeContent)
        assertEquals("https://page.test/", vm.screenState().currentUrl)
        vm.onAction(BrowserAction.Home.OverlayDismissed)
        assertFalse(vm.screenState().isHomeContent)
    }

    @Test
    fun `Home submit loads the same URL as the address bar and marks cameFromHome`() = runTest {
        val vm = newViewModel(UrlConstants.HOME_PAGE_URL)
        advanceUntilIdle()
        val events = eventsOf(vm)
        vm.onAction(BrowserAction.Home.Submit("kotlinlang.org"))
        val fromHome = events.loads().single()
        vm.onAction(BrowserAction.AddressBar.TextChange("kotlinlang.org"))
        vm.onAction(BrowserAction.AddressBar.Submit)
        assertEquals(listOf(fromHome, fromHome), events.loads())
        assertTrue(vm.screenState().cameFromHome)
    }

    @Test
    fun `blank Home submit does nothing`() = runTest {
        val vm = newViewModel(UrlConstants.HOME_PAGE_URL)
        advanceUntilIdle()
        val events = eventsOf(vm)
        vm.onAction(BrowserAction.Home.Submit("   "))
        assertTrue(events.loads().isEmpty())
        assertFalse(vm.screenState().cameFromHome)
    }

    @Test
    fun `Back without history returns to Home only when the page came from Home`() = runTest {
        val vm = newViewModel(UrlConstants.HOME_PAGE_URL)
        advanceUntilIdle()
        val before = vm.screenState()
        vm.onAction(BrowserAction.BackWithoutHistory)
        assertEquals(before, vm.screenState())

        vm.onAction(BrowserAction.Home.NavigateFrom)
        vm.onAction(BrowserAction.Page.UrlChanged("https://page.test/"))
        assertFalse(vm.screenState().isHomeContent)
        vm.onAction(BrowserAction.BackWithoutHistory)
        assertTrue(vm.screenState().isHomeContent)
        assertFalse(vm.screenState().cameFromHome)
    }

    private companion object {
        const val ISOLATION_TICKS = 50
        const val EARLY_PROGRESS: Int = 25
        const val MID_PROGRESS: Int = 50
        const val NEAR_PROGRESS: Int = 99
        const val MAX_PROGRESS: Int = 100
        const val OVER_PROGRESS: Int = 150
        const val NEGATIVE_PROGRESS: Int = -5
        const val FLOAT_TOLERANCE: Float = 0.0001f
    }
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"
