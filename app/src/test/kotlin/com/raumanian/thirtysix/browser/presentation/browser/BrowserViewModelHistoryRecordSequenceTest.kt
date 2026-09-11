package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Spec 014 FR-001 / FR-003 / FR-004 — replays the **real** WebView callback
 * sequences observed on device (emulator API 36, 2026-05-08) rather than the
 * idealised order the MVP pass assumed.
 *
 * Chromium's WebView drives, in order, for one navigation:
 *   `doUpdateVisitedHistory(newUrl)` → `onPageStarted(newUrl)` → progress ticks →
 *   `onProgressChanged(100)` → `onPageFinished(newUrl)`
 *
 * which maps onto `onUrlChanged` → `onLoadStarted` → `onProgressChanged` →
 * `onLoadFinished`. Crucially `onProgressChanged(100)` moves the state to
 * `Loaded` and `onUrlChanged` has already published the new URL — so any
 * "is this an idempotent re-fire?" check based on `(loadingState, currentUrl)`
 * is already satisfied by the time the genuine `onPageFinished` arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelHistoryRecordSequenceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One full navigation as the platform actually sequences it. */
    private fun BrowserViewModel.navigate(url: String) {
        onUrlChanged(url) // doUpdateVisitedHistory
        onUrlChanged(url) // onPageStarted fires it again
        onLoadStarted(url)
        onProgressChanged(PROGRESS_MID)
        onProgressChanged(PROGRESS_DONE)
        onLoadFinished(url)
    }

    @Test
    fun `second navigation is recorded (FR-001)`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.navigate(FIRST_URL)
        advanceUntilIdle()
        vm.navigate(SECOND_URL)
        advanceUntilIdle()

        assertEquals(
            "every successful page load must produce exactly one history row",
            listOf(SECOND_URL, FIRST_URL),
            history.recorded.map { it.url },
        )
    }

    @Test
    fun `onPageFinished re-firing for the same navigation records only once`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.navigate(FIRST_URL)
        vm.onLoadFinished(FIRST_URL) // platform re-fire, same navigation
        vm.onLoadFinished(FIRST_URL)
        advanceUntilIdle()

        assertEquals(1, history.recorded.size)
    }

    @Test
    fun `revisiting the same URL as a new navigation records a second row (FR-004)`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.navigate(FIRST_URL)
        advanceUntilIdle()
        vm.navigate(FIRST_URL)
        advanceUntilIdle()

        assertEquals(2, history.recorded.size)
    }

    @Test
    fun `a failed load is never recorded (FR-003)`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.onUrlChanged(SECOND_URL)
        vm.onLoadStarted(SECOND_URL)
        vm.onLoadFailed(ErrorReason.DnsFailure)
        vm.onLoadFinished(SECOND_URL) // WebView still finishes the error page
        advanceUntilIdle()

        assertEquals(0, history.recorded.size)
    }

    @Test
    fun `a title from the previous page is never attached to the new URL`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.navigate(FIRST_URL)
        vm.onTitleReceived("First Page")
        advanceUntilIdle()

        // Second navigation whose own title never arrives before page-finish.
        vm.navigate(SECOND_URL)
        advanceUntilIdle()

        val second = history.recorded.single { it.url == SECOND_URL }
        assertEquals(
            "the previous page's title must not leak onto a different URL",
            "",
            second.title,
        )
    }

    @Test
    fun `a title arriving after page-finish is backfilled onto the recorded row`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.navigate(FIRST_URL)
        advanceUntilIdle()
        vm.onTitleReceived("First Page") // arrives late, as on a real device
        advanceUntilIdle()

        assertEquals("First Page", history.recorded.single { it.url == FIRST_URL }.title)
    }

    private fun newViewModel(history: FakeHistoryRepository): BrowserViewModel {
        val tabRepo = FakeTabRepository(homeUrl = HOME_URL)
        val incognitoRepo = FakeIncognitoTabRepository()
        val observeAllTabs = ObserveAllTabsUseCase(tabRepo, incognitoRepo)
        val bookmarkRepo = FakeBookmarkRepository()
        return BrowserViewModel(
            defaultHomeUrl = HOME_URL,
            buildSearchUrl = BuildSearchUrlUseCase(NoopSearchEngineRepository),
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            observeTabs = ObserveTabsUseCase(tabRepo),
            observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepo, incognitoRepo),
            createTab = CreateTabUseCase(tabRepo, HOME_URL),
            faviconCache = NoopFaviconCache,
            screenshotCache = NoopScreenshotCache,
            isUrlBookmarked = IsUrlBookmarkedUseCase(bookmarkRepo),
            toggleBookmark = ToggleBookmarkUseCase(bookmarkRepo, AddBookmarkUseCase(bookmarkRepo)),
            recordHistoryEntry = RecordHistoryEntryUseCase(history),
            updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(history),
            startDownload = noOpStartDownloadUseCase(),
        )
    }

    /** Search is irrelevant here; the VM only needs a non-null dependency. */
    private object NoopSearchEngineRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String = query
    }

    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, bitmap: Bitmap) = Unit
        override suspend fun clearAll() = Unit
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

    private companion object {
        const val HOME_URL = "https://home.example.com/"
        const val FIRST_URL = "https://first.example.com/"
        const val SECOND_URL = "https://second.example.com/"
        const val PROGRESS_MID = 40
        const val PROGRESS_DONE = 100
    }
}
