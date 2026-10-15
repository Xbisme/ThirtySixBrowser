package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** JVM tests for [BrowserViewModel]: loading, navigation state, history and progress. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Suppress("LongParameterList")
    private fun newViewModel(
        url: String = SAMPLE_PAGE_URL,
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

    // ---------- onUrlChanged and onLoadStarted ----------

    /** `onLoadStarted` only flips the loading state; [onUrlChanged] owns `currentUrl`. */
    @Test
    fun `onLoadStarted does not mutate currentUrl on load start`() = runTest {
        val vm = newViewModel("https://initial.example.com")
        vm.onAction(BrowserAction.Page.LoadStarted("https://other.example.com"))
        val state = vm.screenState()
        assertEquals("https://initial.example.com", state.currentUrl)
        assertTrue(state.loadingState is LoadingState.Loading)
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
