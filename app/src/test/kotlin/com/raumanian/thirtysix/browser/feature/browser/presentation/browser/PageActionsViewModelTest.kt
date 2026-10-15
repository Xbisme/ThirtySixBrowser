package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import java.io.File
import java.io.OutputStream
import java.net.URLEncoder
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

/** Star-icon paths of [BrowserViewModel]; the star reflects whether any bookmark has the URL. */
@OptIn(ExperimentalCoroutinesApi::class)
class PageActionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun `isBookmarked starts false then flips true after star tap`() = runTest {
        val bookmarkRepo = FakeBookmarkRepository()
        val vm = newViewModel(bookmarkRepo = bookmarkRepo, url = "https://example.com")
        advanceUntilIdle()

        vm.uiState.test {
            // Initial state: URL is example.com, no bookmark exists yet.
            val initial = awaitItem()
            assertEquals(false, initial.isBookmarked)

            val events = eventsOf(vm.events)
            vm.onAction(PageActionsAction.StarTapped)
            advanceUntilIdle()

            // Drain emissions until isBookmarked = true (the snackbar event
            // fires alongside the bookmark add).
            var saw = false
            while (!saw) {
                val state = awaitItem()
                if (state.isBookmarked) {
                    saw = true
                    assertEquals(listOf(PageActionsEvent.Bookmark(BookmarkSnackbarEvent.Added)), events)
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

        val events = eventsOf(vm.events)
        vm.onAction(PageActionsAction.StarTapped)
        advanceUntilIdle()
        assertEquals(1, bookmarkRepo.countBookmarksByUrl("https://example.com"))

        vm.onAction(PageActionsAction.StarTapped)
        advanceUntilIdle()

        assertEquals(0, bookmarkRepo.countBookmarksByUrl("https://example.com"))
        assertEquals(PageActionsEvent.Bookmark(BookmarkSnackbarEvent.Removed), events.last())
        assertEquals(false, vm.uiState.value.isBookmarked)
    }

    @Test
    fun `a bookmark message is delivered once`() = runTest {
        val vm = newViewModel(url = "https://example.com")
        advanceUntilIdle()

        vm.onAction(PageActionsAction.StarTapped)
        advanceUntilIdle()

        vm.events.test {
            assertEquals(PageActionsEvent.Bookmark(BookmarkSnackbarEvent.Added), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `onLongPressNewTab under cap creates a tab without setting tabsEvent`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        val events = eventsOf(vm.events)
        vm.onAction(PageActionsAction.LongPressNewTab)
        advanceUntilIdle()
        // No cap-reached error because we're far below MAX_TABS.
        assertTrue(events.none { it is PageActionsEvent.TabLimit })
    }

    @Test
    fun `onLongPressNewTab at cap sets tabsEvent to MaxTabsReached`() = runTest(testDispatcher) {
        // Pre-fill the FakeTabRepository to MAX_TABS.
        val repository = FakeTabRepository(homeUrl = "https://example.com")
        val seed = (1..BrowserLimits.MAX_TABS).map { i ->
            Tab(
                id = i.toLong(),
                url = "https://t$i.com",
                title = "",
                position = i - 1,
                createdAt = 0L,
                lastActiveAt = i.toLong(),
            )
        }
        repository.emit(seed)
        val vm = newViewModel(url = "https://example.com", tabRepository = repository)
        advanceUntilIdle()

        val events = eventsOf(vm.events)
        vm.onAction(PageActionsAction.LongPressNewTab)
        advanceUntilIdle()

        assertEquals(listOf(PageActionsEvent.TabLimit(TabsErrorEvent.MaxTabsReached)), events)
    }

    @Test
    fun `no tab-limit message is sent without a long press`() = runTest(testDispatcher) {
        val vm = newViewModel()
        val events = eventsOf(vm.events)
        advanceUntilIdle()
        assertTrue(events.none { it is PageActionsEvent.TabLimit })
    }

    private fun newViewModel(
        url: String = SAMPLE_PAGE_URL,
        bookmarkRepo: FakeBookmarkRepository = FakeBookmarkRepository(),
        tabRepository: FakeTabRepository = FakeTabRepository(homeUrl = url),
    ): PageActionsViewModel = PageActionsViewModel(
        defaultHomeUrl = url,
        pageStore = CurrentPageRepositoryImpl(url),
        tabs = tabRepository,
        isUrlBookmarked = IsUrlBookmarkedUseCase(bookmarkRepo),
        toggleBookmark = ToggleBookmarkUseCase(bookmarkRepo, AddBookmarkUseCase(bookmarkRepo)),
    )

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

    private object GoogleByteIdenticalRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            return String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
        }
    }
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"
