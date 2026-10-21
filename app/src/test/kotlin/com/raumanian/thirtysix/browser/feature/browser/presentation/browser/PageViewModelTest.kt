package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** JVM tests for [PageViewModel]: loading, navigation state, history and progress. */
@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

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
    ): PageViewModel {
        // Tab use cases and caches share one self-contained fake by default.
        val incognitoRepo = FakeIncognitoTabRepository()
        val observeAllTabs = ObserveAllTabsUseCase(tabRepository, incognitoRepo)
        val observeActiveTab = ObserveActiveTabUseCase(observeAllTabs)
        return PageViewModel(
            observeActiveTab = observeActiveTab,
            tabs = tabRepository,
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
        vm.onAction(PageAction.UrlChanged("https://example.com"))
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(EARLY_PROGRESS))
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS))
        vm.onAction(PageAction.ProgressChanged(MAX_PROGRESS))

        val state = vm.screenState()
        assertEquals("https://example.com", state.currentUrl)
        assertEquals(LoadingState.Loaded, state.loadingState)
    }

    @Test
    fun `loaded via onPageFinished without final progress event`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS)) // never reaches 100
        vm.onAction(PageAction.LoadFinished("https://example.com"))

        val state = vm.screenState()
        assertEquals(LoadingState.Loaded, state.loadingState)
    }

    @Test
    fun `onLoadFinished is idempotent`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFinished("https://example.com"))
        val firstLoaded = vm.screenState()
        vm.onAction(PageAction.LoadFinished("https://example.com")) // second call
        val secondLoaded = vm.screenState()

        assertEquals(firstLoaded, secondLoaded)
        assertEquals(LoadingState.Loaded, secondLoaded.loadingState)
    }

    @Test
    fun `progress over 100 is clamped to Loaded`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(OVER_PROGRESS)) // defensive: 150
        assertEquals(LoadingState.Loaded, vm.screenState().loadingState)
    }

    @Test
    fun `progress under 0 is clamped to Loading 0f`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(NEGATIVE_PROGRESS)) // defensive: -5
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Loading)
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    @Test
    fun `transitions to Failed on onLoadFailed during loading`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Failed)
        assertEquals(ErrorReason.NetworkUnavailable, (state.loadingState as LoadingState.Failed).reason)
    }

    @Test
    fun `Loaded then onLoadStarted again then onLoadFailed lands on Failed`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFinished("https://example.com"))
        vm.onAction(PageAction.LoadStarted("https://other.example.com")) // simulating a reload
        vm.onAction(PageAction.LoadFailed(ErrorReason.SslError))
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Failed)
        assertEquals(ErrorReason.SslError, (state.loadingState as LoadingState.Failed).reason)
    }

    @Test
    fun `recovery from Failed via onLoadStarted re-enters Loading 0f`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFailed(ErrorReason.DnsFailure))
        // Now retry
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        val state = vm.screenState()
        assertTrue(state.loadingState is LoadingState.Loading)
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    @Test
    fun `engine error page progress and finish keep Failed and record no history`() =
        runTest(testDispatcher) {
            val historyRepo = FakeHistoryRepository()
            val vm = newViewModel(historyRepository = historyRepo)
            advanceUntilIdle()
            vm.onAction(PageAction.LoadStarted("https://nowhere.invalid"))
            vm.onAction(PageAction.LoadFailed(ErrorReason.DnsFailure))
            vm.onAction(PageAction.ProgressChanged(MID_PROGRESS))
            vm.onAction(PageAction.ProgressChanged(MAX_PROGRESS))
            vm.onAction(PageAction.LoadFinished("https://nowhere.invalid"))
            advanceUntilIdle()
            assertEquals(LoadingState.Failed(ErrorReason.DnsFailure), vm.screenState().loadingState)
            assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
            assertTrue(historyRepo.recorded.isEmpty())

            vm.onAction(PageAction.LoadStarted("https://example.com"))
            assertEquals(LoadingState.Loading, vm.screenState().loadingState)
        }

    @Test
    fun `progress integer maps to expected Float in 0_to_1f range`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(0))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
        vm.onAction(PageAction.ProgressChanged(EARLY_PROGRESS)) // 25
        assertEquals(0.25f, vm.loadProgress.value, FLOAT_TOLERANCE)
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS)) // 50
        assertEquals(0.5f, vm.loadProgress.value, FLOAT_TOLERANCE)
        vm.onAction(PageAction.ProgressChanged(NEAR_PROGRESS)) // 99
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
        vm.onAction(PageAction.CanGoBackChanged(true))
        assertEquals(true, vm.screenState().canGoBack)
        assertEquals(false, vm.screenState().canGoForward) // independent
    }

    @Test
    fun `onCanGoBackChanged false reverts to false`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.CanGoBackChanged(true))
        vm.onAction(PageAction.CanGoBackChanged(false))
        assertEquals(false, vm.screenState().canGoBack)
    }

    @Test
    fun `onCanGoForwardChanged true propagates to state`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.CanGoForwardChanged(true))
        assertEquals(true, vm.screenState().canGoForward)
        assertEquals(false, vm.screenState().canGoBack) // independent
    }

    @Test
    fun `onCanGoBackChanged and onCanGoForwardChanged are independent`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.CanGoBackChanged(true))
        vm.onAction(PageAction.CanGoForwardChanged(true))
        val state = vm.screenState()
        assertEquals(true, state.canGoBack)
        assertEquals(true, state.canGoForward)

        vm.onAction(PageAction.CanGoBackChanged(false))
        val after = vm.screenState()
        assertEquals(false, after.canGoBack)
        assertEquals(true, after.canGoForward) // forward retains its value
    }

    // ---------- onLoadStopped ----------

    @Test
    fun `onLoadStopped during Loading transitions to Loaded`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS)) // partial load
        assertTrue(vm.screenState().loadingState is LoadingState.Loading)

        vm.onAction(PageAction.LoadStopped)
        assertEquals(LoadingState.Loaded, vm.screenState().loadingState)
    }

    @Test
    fun `onLoadStopped from non-Loading state is no-op`() = runTest {
        val vm = newViewModel()
        // From Idle
        vm.onAction(PageAction.LoadStopped)
        assertEquals(LoadingState.Idle, vm.screenState().loadingState)

        // From Loaded
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFinished("https://example.com"))
        val loaded = vm.screenState()
        vm.onAction(PageAction.LoadStopped)
        assertEquals(loaded, vm.screenState())

        // From Failed
        vm.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
        val failed = vm.screenState()
        vm.onAction(PageAction.LoadStopped)
        assertEquals(failed, vm.screenState())
    }

    // ---------- onUrlChanged and onLoadStarted ----------

    /** `onLoadStarted` only flips the loading state; [onUrlChanged] owns `currentUrl`. */
    @Test
    fun `onLoadStarted does not mutate currentUrl on load start`() = runTest {
        val vm = newViewModel("https://initial.example.com")
        vm.onAction(PageAction.LoadStarted("https://other.example.com"))
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
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.TitleReceived("Example"))
        vm.onAction(PageAction.LoadFinished("https://example.com"))
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
            vm.onAction(PageAction.LoadStarted("https://example.com"))
            vm.onAction(PageAction.TitleReceived("Example"))
            vm.onAction(PageAction.ProgressChanged(MAX_PROGRESS)) // marks state Loaded
            // Idempotent on Loaded + same URL: no record.
            vm.onAction(PageAction.LoadFinished("https://example.com"))
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
            vm.onAction(PageAction.LoadStarted("https://example.com"))
            vm.onAction(PageAction.LoadFinished("https://example.com"))
            advanceUntilIdle()
            // Reload (state transitions from Loaded → Loading → Loaded).
            vm.onAction(PageAction.LoadStarted("https://example.com"))
            vm.onAction(PageAction.LoadFinished("https://example.com"))
            advanceUntilIdle()
            assertEquals(2, historyRepo.recorded.size)
        }

    @Test
    fun `onLoadFailed never invokes the recorder`() =
        runTest(testDispatcher) {
            val historyRepo = FakeHistoryRepository()
            val vm = newViewModel(historyRepository = historyRepo)
            advanceUntilIdle()
            vm.onAction(PageAction.LoadStarted("https://example.com"))
            vm.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
            advanceUntilIdle()
            assertEquals(0, historyRepo.recorded.size)
        }

    // ---------- Progress kept out of uiState ----------

    @Test
    fun `fifty progress ticks emit fifty progress values and no uiState`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(1)) // enters Loading once; isolation is about what follows
        vm.uiState.test {
            awaitItem()
            val seen = mutableListOf<Float>()
            vm.loadProgress.test {
                awaitItem()
                for (tick in 2..ISOLATION_TICKS + 1) {
                    vm.onAction(PageAction.ProgressChanged(tick))
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
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS))
        vm.onAction(PageAction.LoadFinished("https://example.com"))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)

        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS))
        vm.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)

        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS))
        vm.onAction(PageAction.LoadStopped)
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)

        vm.onAction(PageAction.ProgressChanged(MID_PROGRESS))
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        assertEquals(0f, vm.loadProgress.value, FLOAT_TOLERANCE)
    }

    // ---------- After a failure ----------

    @Test
    fun `a tab switch while Failed goes back to Idle`() = runTest(testDispatcher) {
        val repo = FakeTabRepository(initialTabs = listOf(tab(TAB_A, lastActiveAt = 2L), tab(TAB_B, lastActiveAt = 1L)))
        val vm = newViewModel(tabRepository = repo)
        advanceUntilIdle()
        vm.onAction(PageAction.LoadStarted("https://missing.example"))
        vm.onAction(PageAction.LoadFailed(ErrorReason.DnsFailure))
        assertTrue(vm.screenState().loadingState is LoadingState.Failed)

        repo.switchActiveTab(TAB_B)
        advanceUntilIdle()

        assertEquals(TAB_B, vm.currentPage.value.activeTab?.id)
        assertEquals(LoadingState.Idle, vm.screenState().loadingState)
    }

    @Test
    fun `the error page title changes neither the page, the tab nor the history row`() = runTest(testDispatcher) {
        val repo = FakeTabRepository()
        val historyRepo = FakeHistoryRepository()
        val vm = newViewModel(tabRepository = repo, historyRepository = historyRepo)
        advanceUntilIdle()
        vm.onAction(PageAction.UrlChanged("https://example.com"))
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFinished("https://example.com"))
        advanceUntilIdle()
        vm.onAction(PageAction.TitleReceived("Example"))
        advanceUntilIdle()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
        val titleBefore = vm.currentPage.value.title

        vm.onAction(PageAction.TitleReceived(ERROR_PAGE_TITLE))
        advanceUntilIdle()

        assertEquals(titleBefore, vm.currentPage.value.title)
        assertEquals("Example", historyRepo.recorded.single().title)
        assertTrue(repo.observeTabs().first().none { it.title == ERROR_PAGE_TITLE })
    }

    @Test
    fun `Retry while Failed shows Loading at once`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))

        vm.onAction(PageAction.Retry)

        assertEquals(LoadingState.Loading, vm.screenState().loadingState)
    }

    @Test
    fun `Retry on a loaded page leaves the state alone`() = runTest {
        val vm = newViewModel()
        vm.onAction(PageAction.LoadStarted("https://example.com"))
        vm.onAction(PageAction.LoadFinished("https://example.com"))

        vm.onAction(PageAction.Retry)

        assertEquals(LoadingState.Loaded, vm.screenState().loadingState)
    }

    private fun tab(id: Long, lastActiveAt: Long) = Tab(
        id = id,
        url = "https://tab$id.example",
        title = "",
        position = id.toInt(),
        createdAt = lastActiveAt,
        lastActiveAt = lastActiveAt,
    )

    // ---------- Home inside a tab ----------

    @Test
    fun `a tab at the Home address shows Home content`() = runTest {
        val vm = newViewModel(UrlConstants.HOME_PAGE_URL)
        advanceUntilIdle()
        assertTrue(vm.screenState().isHomeContent)
    }

    private companion object {
        const val ISOLATION_TICKS = 50
        const val TAB_A: Long = 1L
        const val TAB_B: Long = 2L
        const val ERROR_PAGE_TITLE: String = "Webpage not available"
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
