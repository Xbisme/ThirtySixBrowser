package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
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
import com.raumanian.thirtysix.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.io.File
import java.net.URLEncoder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 013 — additive coverage for [BrowserViewModel] star-icon paths
 * (FR-001..003 + the canonical-most-recent semantics from R9 / Q4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelStarToggleTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isBookmarked starts false then flips true after star tap`() = runTest {
        val bookmarkRepo = FakeBookmarkRepository()
        val vm = newViewModel(bookmarkRepo = bookmarkRepo, url = "https://example.com")
        advanceUntilIdle()

        vm.uiState.test {
            // Initial state: URL is example.com, no bookmark exists yet.
            val initial = awaitItem()
            assertEquals(false, initial.isBookmarked)

            vm.onStarTapped()
            advanceUntilIdle()

            // Drain emissions until isBookmarked = true (the snackbar event
            // fires alongside the bookmark add).
            var saw = false
            while (!saw) {
                val state = awaitItem()
                if (state.isBookmarked) {
                    saw = true
                    assertEquals(BookmarkSnackbarEvent.Added, state.bookmarkSnackbarEvent)
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, bookmarkRepo.countBookmarksByUrl("https://example.com"))
    }

    @Test
    fun `second star tap removes the bookmark and emits Removed snackbar`() = runTest {
        val bookmarkRepo = FakeBookmarkRepository()
        val vm = newViewModel(bookmarkRepo = bookmarkRepo, url = "https://example.com")
        advanceUntilIdle()

        vm.onStarTapped()
        advanceUntilIdle()
        vm.consumeBookmarkSnackbarEvent()
        assertEquals(1, bookmarkRepo.countBookmarksByUrl("https://example.com"))

        vm.onStarTapped()
        advanceUntilIdle()

        assertEquals(0, bookmarkRepo.countBookmarksByUrl("https://example.com"))
        assertEquals(BookmarkSnackbarEvent.Removed, vm.uiState.value.bookmarkSnackbarEvent)
        assertEquals(false, vm.uiState.value.isBookmarked)
    }

    @Test
    fun `consumeBookmarkSnackbarEvent clears the event idempotently`() = runTest {
        val vm = newViewModel(url = "https://example.com")
        advanceUntilIdle()

        vm.onStarTapped()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.bookmarkSnackbarEvent != null)

        vm.consumeBookmarkSnackbarEvent()
        assertNull(vm.uiState.value.bookmarkSnackbarEvent)

        // Second call is a no-op.
        vm.consumeBookmarkSnackbarEvent()
        assertNull(vm.uiState.value.bookmarkSnackbarEvent)
    }

    private fun newViewModel(
        url: String = UrlConstants.DEFAULT_HOME_URL,
        bookmarkRepo: FakeBookmarkRepository = FakeBookmarkRepository(),
    ): BrowserViewModel {
        val tabRepository = FakeTabRepository(homeUrl = url)
        val incognitoRepo = FakeIncognitoTabRepository()
        val observeTabs = ObserveTabsUseCase(tabRepository)
        val observeAllTabs = ObserveAllTabsUseCase(tabRepository, incognitoRepo)
        val observeActiveTab = ObserveActiveTabUseCase(observeAllTabs)
        val observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs)
        val addBookmark = AddBookmarkUseCase(bookmarkRepo)
        val toggleBookmark = ToggleBookmarkUseCase(bookmarkRepo, addBookmark)
        val isUrlBookmarked = IsUrlBookmarkedUseCase(bookmarkRepo)
        return BrowserViewModel(
            defaultHomeUrl = url,
            buildSearchUrl = BuildSearchUrlUseCase(GoogleByteIdenticalRepository),
            observeActiveTab = observeActiveTab,
            observeTabs = observeTabs,
            observeActiveTabIsIncognito = observeActiveTabIsIncognito,
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepository, incognitoRepo),
            createTab = CreateTabUseCase(tabRepository, url),
            faviconCache = NoopFaviconCache,
            screenshotCache = NoopScreenshotCache,
            isUrlBookmarked = isUrlBookmarked,
            toggleBookmark = toggleBookmark,
            recordHistoryEntry = com.raumanian.thirtysix.browser.domain.usecase.RecordHistoryEntryUseCase(
                com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository(),
            ),
            updateHistoryEntryTitle = com.raumanian.thirtysix.browser.domain.usecase
                .UpdateHistoryEntryTitleUseCase(
                    com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository(),
                ),
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

    private object GoogleByteIdenticalRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            return String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
        }
    }
}
