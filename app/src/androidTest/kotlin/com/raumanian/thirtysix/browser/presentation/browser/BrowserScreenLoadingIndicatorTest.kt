package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
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
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_BROWSER_LOADING_INDICATOR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 007 US2 — loading-indicator rendering branch of [BrowserScreen].
 *
 * Answers exactly one question: does the Compose binding between
 * `BrowserUiState.loadingState` and [components.BrowserLoadingIndicator] work —
 * indicator present while `Loading`, absent otherwise.
 *
 * **Why this lives in its own class instead of `BrowserScreenInstrumentedTest`.**
 * The assertion used to run there against a live `example.com` load, and it was
 * order-dependent: once a sibling test in that class had warmed the WebView's HTTP
 * cache, the `Loading` window closed faster than the assertion could observe it.
 * Confirmed 2026-05-08 — it reproduced on `main` (commit `954742a`) in a clean
 * worktree, on both an API 24 and an API 36 emulator, and on CI's API 29 runner,
 * while passing 3/3 in isolation. Waiting for the page to settle first was **not**
 * enough either: a settled WebView can still re-fire `onProgressChanged` /
 * `onPageFinished`, which flips the synthetic `Loading` straight back to `Loaded`,
 * and CI's software-rendered (`swiftshader_indirect`) emulator widens that window
 * enough to lose the race every time.
 *
 * So the WebView is removed from the equation entirely, reusing the seam
 * `BrowserScreenOfflineErrorTest` established: seed the ViewModel to
 * [LoadingState.Failed] **before** `setContent`, because
 * [BrowserWebView] skips its initial `loadUrl` in that state (see the conditional
 * in `buildConfiguredWebView`). The WebView is constructed but never loads
 * anything, so it emits no callbacks, and the state machine can be driven
 * deterministically — no network, no cache warmth, no ordering dependency.
 *
 * The live-load path stays covered by
 * `BrowserScreenInstrumentedTest.pageRenders_assertsDomContainsExampleDomain`, and
 * the platform's real callback ordering by `BrowserViewModelHistoryRecordSequenceTest`
 * on the JVM.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BrowserScreenLoadingIndicatorTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            val noopTabRepo = LoadingIndicatorNoopTabRepository
            val noopIncognitoRepo = LoadingIndicatorNoopIncognitoTabRepository
            val observeAllTabs = ObserveAllTabsUseCase(noopTabRepo, noopIncognitoRepo)
            viewModel = BrowserViewModel(
                defaultHomeUrl = UrlConstants.DEFAULT_HOME_URL,
                buildSearchUrl = BuildSearchUrlUseCase(LoadingIndicatorNoopSearchEngineRepository),
                observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
                observeTabs = ObserveTabsUseCase(noopTabRepo),
                observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
                updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(
                    noopTabRepo,
                    noopIncognitoRepo,
                ),
                createTab = CreateTabUseCase(noopTabRepo, UrlConstants.DEFAULT_HOME_URL),
                faviconCache = LoadingIndicatorNoopFaviconCache,
                screenshotCache = LoadingIndicatorNoopScreenshotCache,
                isUrlBookmarked = IsUrlBookmarkedUseCase(LoadingIndicatorNoopBookmarkRepository),
                toggleBookmark = ToggleBookmarkUseCase(
                    LoadingIndicatorNoopBookmarkRepository,
                    AddBookmarkUseCase(LoadingIndicatorNoopBookmarkRepository),
                ),
                recordHistoryEntry = RecordHistoryEntryUseCase(LoadingIndicatorNoopHistoryRepository),
                updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(
                    LoadingIndicatorNoopHistoryRepository,
                ),
                startDownload = instrumentedNoOpStartDownloadUseCase(),
            ).apply {
                // Seeded BEFORE setContent so `BrowserWebView` skips its initial
                // `loadUrl` — the WebView then never fires a single callback.
                onLoadStarted(UrlConstants.DEFAULT_HOME_URL)
                onLoadFailed(ErrorReason.NetworkUnavailable)
            }
            composeRule.activity.setContent { BrowserScreen(viewModel = viewModel) }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingIndicator_appearsWhileLoadingAndHidesWhenLoaded() {
        composeRule.activity.runOnUiThread {
            viewModel.onLoadStarted(UrlConstants.DEFAULT_HOME_URL)
            viewModel.onProgressChanged(PROGRESS_MIDWAY)
        }
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )

        composeRule.activity.runOnUiThread {
            viewModel.onProgressChanged(PROGRESS_COMPLETE)
            viewModel.onLoadFinished(UrlConstants.DEFAULT_HOME_URL)
        }
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingIndicator_isAbsentWhileTheLoadHasFailed() {
        // The seeded state is Failed, which is neither Loading nor Loaded — the
        // indicator must not be rendered for it.
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )
    }

    private companion object {
        /** Pure recomposition against pre-seeded state — no network in the loop. */
        const val INDICATOR_TIMEOUT_MS: Long = 5_000L

        /** Any value below 100 maps to `LoadingState.Loading`. */
        const val PROGRESS_MIDWAY: Int = 40

        /** 100 maps to `LoadingState.Loaded`. */
        const val PROGRESS_COMPLETE: Int = 100
    }
}

private object LoadingIndicatorNoopSearchEngineRepository : SearchEngineRepository {
    override suspend fun buildSearchUrl(query: String): String =
        error("loading-indicator test should not reach the search-URL build path")
}

/** Hot StateFlow (NOT flowOf) so `ObserveAllTabsUseCase.combine` stays subscribed. */
private object LoadingIndicatorNoopTabRepository : TabRepository {
    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("loading-indicator test should not reach createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAllTabs() = Unit
    override suspend fun getTabCount(): Int = 0
}

private object LoadingIndicatorNoopIncognitoTabRepository : IncognitoTabRepository {
    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("loading-indicator test should not reach incognito createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAll() = Unit
    override suspend fun getCount(): Int = 0
}

private object LoadingIndicatorNoopFaviconCache : FaviconCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(url: String, bitmap: Bitmap) = Unit
    override suspend fun clearAll() = Unit
    override fun fileFor(url: String): File? = null
}

private object LoadingIndicatorNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, bitmap: Bitmap) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}

private object LoadingIndicatorNoopBookmarkRepository :
    com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository {
    override fun observeBookmarksByFolder(folderId: Long?) =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.domain.model.Bookmark>())
    override fun observeBookmarksByUrl(url: String) =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.domain.model.Bookmark>())
    override fun searchBookmarks(query: String) =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.domain.model.Bookmark>())
    override suspend fun getBookmark(id: Long) = null
    override suspend fun countBookmarksByUrl(url: String) = 0
    override suspend fun addBookmark(bookmark: com.raumanian.thirtysix.browser.domain.model.Bookmark) =
        Result.Success(0L)
    override suspend fun updateBookmark(bookmark: com.raumanian.thirtysix.browser.domain.model.Bookmark) =
        Result.Success(Unit)
    override suspend fun deleteBookmark(id: Long) = Result.Success(Unit)
    override suspend fun deleteMostRecentBookmarkByUrl(url: String) = Result.Success(0)
    override suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?) =
        Result.Success(Unit)
    override fun observeFoldersByParent(parentId: Long?) =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.domain.model.BookmarkFolder>())
    override fun observeAllFolders() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.domain.model.BookmarkFolder>())
    override suspend fun getFolder(id: Long) = null
    override suspend fun getAncestorChain(folderId: Long) =
        emptyList<com.raumanian.thirtysix.browser.domain.model.BookmarkFolder>()
    override suspend fun countDescendants(folderId: Long) =
        com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount(0, 0)
    override suspend fun createFolder(name: String, parentId: Long?) = Result.Success(0L)
    override suspend fun renameFolder(folderId: Long, newName: String) = Result.Success(Unit)
    override suspend fun moveFolder(folderId: Long, newParentId: Long?) = Result.Success(Unit)
    override suspend fun deleteFolderCascade(folderId: Long) =
        Result.Success(com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount(0, 0))
}

private object LoadingIndicatorNoopHistoryRepository :
    com.raumanian.thirtysix.browser.domain.repository.HistoryRepository {
    override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long = 0L
    override fun observeAll() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.domain.model.HistoryEntry>())
    override suspend fun pruneOlderThan(cutoffMillis: Long): Int = 0
    override suspend fun updateTitle(id: Long, title: String): Int = 0
    override suspend fun deleteById(id: Long): Int = 0
    override suspend fun clearAll(): Int = 0
    override suspend fun count(): Int = 0
}
