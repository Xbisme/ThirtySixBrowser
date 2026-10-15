package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
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
import org.junit.Rule
import org.junit.Test

/**
 * Replays the callback order WebView really uses for one navigation: `doUpdateVisitedHistory` →
 * `onPageStarted` → progress → `onProgressChanged(100)` → `onPageFinished`. By the last step the
 * state is already Loaded with the new URL, so recording cannot be inferred from that state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelHistoryRecordSequenceTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    /** One full navigation as the platform actually sequences it. */
    private fun BrowserViewModel.navigate(url: String) {
        onAction(BrowserAction.Page.UrlChanged(url)) // doUpdateVisitedHistory
        onAction(BrowserAction.Page.UrlChanged(url)) // onPageStarted fires it again
        onAction(BrowserAction.Page.LoadStarted(url))
        onAction(BrowserAction.Page.ProgressChanged(PROGRESS_MID))
        onAction(BrowserAction.Page.ProgressChanged(PROGRESS_DONE))
        onAction(BrowserAction.Page.LoadFinished(url))
    }

    @Test
    fun `second navigation is recorded`() = runTest {
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
        vm.onAction(BrowserAction.Page.LoadFinished(FIRST_URL)) // platform re-fire, same navigation
        vm.onAction(BrowserAction.Page.LoadFinished(FIRST_URL))
        advanceUntilIdle()

        assertEquals(1, history.recorded.size)
    }

    @Test
    fun `revisiting the same URL as a new navigation records a second row`() = runTest {
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
    fun `a failed load is never recorded`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.onAction(BrowserAction.Page.UrlChanged(SECOND_URL))
        vm.onAction(BrowserAction.Page.LoadStarted(SECOND_URL))
        vm.onAction(BrowserAction.Page.LoadFailed(ErrorReason.DnsFailure))
        vm.onAction(BrowserAction.Page.LoadFinished(SECOND_URL)) // WebView still finishes the error page
        advanceUntilIdle()

        assertEquals(0, history.recorded.size)
    }

    @Test
    fun `a title from the previous page is never attached to the new URL`() = runTest {
        val history = FakeHistoryRepository()
        val vm = newViewModel(history)
        advanceUntilIdle()

        vm.navigate(FIRST_URL)
        vm.onAction(BrowserAction.Page.TitleReceived("First Page"))
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
        vm.onAction(BrowserAction.Page.TitleReceived("First Page")) // arrives late, as on a real device
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
            searchEngine = NoopSearchEngineRepository,
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            tabs = tabRepo,
            observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepo, incognitoRepo),
            faviconCache = NoopFaviconCache,
            screenshotCache = NoopScreenshotCache,
            isUrlBookmarked = IsUrlBookmarkedUseCase(bookmarkRepo),
            toggleBookmark = ToggleBookmarkUseCase(bookmarkRepo, AddBookmarkUseCase(bookmarkRepo)),
            recordHistoryEntry = RecordHistoryEntryUseCase(history),
            updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(history),
            pageStore = CurrentPageRepositoryImpl(HOME_URL),
        )
    }

    /** Search is irrelevant here; the VM only needs a non-null dependency. */
    private object NoopSearchEngineRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String = query
    }

    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun retainOnly(hosts: Set<String>) = Unit
        override fun fileFor(url: String): File? = null
    }

    private object NoopScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
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
