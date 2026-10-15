package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_PAGE_LOAD_PROGRESS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.OutputStream
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The loading indicator shows while `Loading` and not otherwise. The view model is seeded to Failed
 * before `setContent`, so the WebView never loads or fires a callback and the state is driven
 * deterministically; against a live load, a warm cache or a late re-fire made the check flaky.
 */
@HiltAndroidTest
// The browser screen reads settings (text size), so each test gets its own DataStore file:
// a fresh Hilt component per test would otherwise open a second one ("multiple DataStores active").
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenLoadingIndicatorTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb") },
            )
    }

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
                defaultHomeUrl = SAMPLE_PAGE_URL,
                searchEngine = LoadingIndicatorNoopSearchEngineRepository,
                observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
                tabs = noopTabRepo,
                observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
                updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(
                    noopTabRepo,
                    noopIncognitoRepo,
                ),
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
                // Seeded before setContent so `BrowserWebView` skips its initial `loadUrl`.
                onAction(BrowserAction.Page.LoadStarted(SAMPLE_PAGE_URL))
                onAction(BrowserAction.Page.LoadFailed(ErrorReason.NetworkUnavailable))
            }
            composeRule.activity.setContent {
                BrowserRoute(
                    slots = NoSlots,
                    viewModel = viewModel,
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingIndicator_appearsWhileLoadingAndHidesWhenLoaded() {
        composeRule.activity.runOnUiThread {
            viewModel.onAction(BrowserAction.Page.LoadStarted(SAMPLE_PAGE_URL))
            viewModel.onAction(BrowserAction.Page.ProgressChanged(PROGRESS_MIDWAY))
        }
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )

        composeRule.activity.runOnUiThread {
            viewModel.onAction(BrowserAction.Page.ProgressChanged(PROGRESS_COMPLETE))
            viewModel.onAction(BrowserAction.Page.LoadFinished(SAMPLE_PAGE_URL))
        }
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingIndicator_isAbsentWhileTheLoadHasFailed() {
        // Failed is neither Loading nor Loaded, so no indicator.
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
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

/** Hot StateFlow, not flowOf, so `ObserveAllTabsUseCase.combine` stays subscribed. */
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
    override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun retainOnly(hosts: Set<String>) = Unit
    override fun fileFor(url: String): File? = null
}

private object LoadingIndicatorNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}

private object LoadingIndicatorNoopBookmarkRepository :
    BookmarkRepository {
    override fun observeBookmarksByFolder(folderId: Long?) =
        kotlinx.coroutines.flow.flowOf(emptyList<Bookmark>())
    override fun observeBookmarksByUrl(url: String) =
        kotlinx.coroutines.flow.flowOf(emptyList<Bookmark>())
    override fun observeRecentBookmarks(limit: Int) =
        kotlinx.coroutines.flow.flowOf(emptyList<Bookmark>())
    override suspend fun getAllBookmarks() = emptyList<Bookmark>()

    override fun searchBookmarks(query: String) =
        kotlinx.coroutines.flow.flowOf(emptyList<Bookmark>())
    override suspend fun getBookmark(id: Long) = null
    override suspend fun countBookmarksByUrl(url: String) = 0
    override suspend fun addBookmark(bookmark: Bookmark) =
        Result.Success(0L)
    override suspend fun updateBookmark(bookmark: Bookmark) =
        Result.Success(Unit)
    override suspend fun deleteBookmark(id: Long) = Result.Success(Unit)
    override suspend fun deleteMostRecentBookmarkByUrl(url: String) = Result.Success(0)
    override suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?) =
        Result.Success(Unit)
    override fun observeFoldersByParent(parentId: Long?) =
        kotlinx.coroutines.flow.flowOf(emptyList<BookmarkFolder>())
    override fun observeAllFolders() =
        kotlinx.coroutines.flow.flowOf(emptyList<BookmarkFolder>())
    override suspend fun getFolder(id: Long) = null
    override suspend fun getAncestorChain(folderId: Long) =
        emptyList<BookmarkFolder>()
    override suspend fun countDescendants(folderId: Long) =
        BookmarkDescendantCount(0, 0)
    override suspend fun createFolder(name: String, parentId: Long?) = Result.Success(0L)
    override suspend fun renameFolder(folderId: Long, newName: String) = Result.Success(Unit)
    override suspend fun moveFolder(folderId: Long, newParentId: Long?) = Result.Success(Unit)
    override suspend fun deleteFolderCascade(folderId: Long) =
        Result.Success(BookmarkDescendantCount(0, 0))
}

private object LoadingIndicatorNoopHistoryRepository :
    HistoryRepository {
    override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long = 0L
    override fun observeAll() =
        kotlinx.coroutines.flow.flowOf(emptyList<HistoryEntry>())
    override suspend fun pruneOlderThan(cutoffMillis: Long): Int = 0
    override suspend fun updateTitle(id: Long, title: String): Int = 0
    override suspend fun deleteById(id: Long): Int = 0
    override suspend fun clearAll(): Int = 0
    override suspend fun count(): Int = 0
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"

private val NoSlots = BrowserSlots(
    fireButton = FireButton(enabled = true, onClick = {}),
    fireOverlay = {},
    quickSettings = { _, _, _ -> },
)
