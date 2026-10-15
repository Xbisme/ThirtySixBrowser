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
import com.raumanian.thirtysix.browser.core.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_BROWSER_ERROR_STATE
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
 * With the state seeded to Failed, the screen shows the error UI instead of the WebView. A real
 * failing load was unreliable on the emulator; the error mapping itself is covered by JVM tests.
 */
@HiltAndroidTest
// The browser screen reads settings (text size), so each test gets its own DataStore file:
// a fresh Hilt component per test would otherwise open a second one ("multiple DataStores active").
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenOfflineErrorTest {

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
            // Seeded to Failed before setContent: the WebView never loads, so nothing can flip the
            // state back to Loaded before the assertion.
            val noopTabRepo = OfflineErrorNoopTabRepository
            val noopIncognitoRepo = OfflineErrorNoopIncognitoTabRepository
            val observeTabs = ObserveTabsUseCase(noopTabRepo)
            val observeAllTabs = ObserveAllTabsUseCase(noopTabRepo, noopIncognitoRepo)
            viewModel = BrowserViewModel(
                defaultHomeUrl = SAMPLE_PAGE_URL,
                buildSearchUrl = BuildSearchUrlUseCase(OfflineErrorNoopSearchEngineRepository),
                observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
                observeTabs = observeTabs,
                observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
                updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(noopTabRepo, noopIncognitoRepo),
                createTab = CreateTabUseCase(noopTabRepo, SAMPLE_PAGE_URL),
                faviconCache = OfflineErrorNoopFaviconCache,
                screenshotCache = OfflineErrorNoopScreenshotCache,
                isUrlBookmarked = IsUrlBookmarkedUseCase(OfflineErrorNoopBookmarkRepository),
                toggleBookmark = ToggleBookmarkUseCase(
                    OfflineErrorNoopBookmarkRepository,
                    AddBookmarkUseCase(OfflineErrorNoopBookmarkRepository),
                ),
                recordHistoryEntry = RecordHistoryEntryUseCase(
                    OfflineErrorNoopHistoryRepository,
                ),
                updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(OfflineErrorNoopHistoryRepository),
                startDownload = instrumentedNoOpStartDownloadUseCase(),
            ).apply {
                onLoadStarted(SAMPLE_PAGE_URL)
                onLoadFailed(ErrorReason.NetworkUnavailable)
            }
            composeRule.activity.setContent {
                BrowserScreen(
                    slots = NoSlots,
                    viewModel = viewModel,
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorState_rendersWhenInitialStateIsFailed() {
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = ERROR_STATE_TIMEOUT_MS,
        )
    }

    private companion object {
        // Pure recomposition with the state pre-seeded; no network involved.
        const val ERROR_STATE_TIMEOUT_MS: Long = 5_000L
    }
}

/** No-op [SearchEngineRepository]: the query branch is never reached. */
private object OfflineErrorNoopSearchEngineRepository : SearchEngineRepository {
    override suspend fun buildSearchUrl(query: String): String =
        error("instrumented test should not reach the search-URL build path")
}

/** No-op [TabRepository] for the error-rendering test. */
private object OfflineErrorNoopTabRepository : TabRepository {
    // Hot, never completing, so ObserveAllTabsUseCase.combine stays subscribed.
    private val state: kotlinx.coroutines.flow.MutableStateFlow<List<Tab>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("error-rendering test should not reach createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAllTabs() = Unit
    override suspend fun getTabCount(): Int = 0
}

/** No-op [IncognitoTabRepository] for the error-rendering test. */
private object OfflineErrorNoopIncognitoTabRepository : IncognitoTabRepository {
    private val state: kotlinx.coroutines.flow.MutableStateFlow<List<Tab>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("error-rendering test should not reach incognito createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAll() = Unit
    override suspend fun getCount(): Int = 0
}

/** No-op [FaviconCache]: no WebView load, so no icon ever arrives. */
private object OfflineErrorNoopFaviconCache : FaviconCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun retainOnly(hosts: Set<String>) = Unit
    override fun fileFor(url: String): File? = null
}

private object OfflineErrorNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}

private object OfflineErrorNoopBookmarkRepository :
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
    override suspend fun deleteBookmark(id: Long) =
        Result.Success(Unit)
    override suspend fun deleteMostRecentBookmarkByUrl(url: String) =
        Result.Success(0)
    override suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?) =
        Result.Success(Unit)
    override fun observeFoldersByParent(parentId: Long?) =
        kotlinx.coroutines.flow.flowOf(emptyList<BookmarkFolder>())
    override fun observeAllFolders() =
        kotlinx.coroutines.flow.flowOf(emptyList<BookmarkFolder>())
    override suspend fun getFolder(id: Long) = null
    override suspend fun getAncestorChain(folderId: Long) = emptyList<BookmarkFolder>()
    override suspend fun countDescendants(folderId: Long) =
        BookmarkDescendantCount(0, 0)
    override suspend fun createFolder(name: String, parentId: Long?) =
        Result.Success(0L)
    override suspend fun renameFolder(folderId: Long, newName: String) =
        Result.Success(Unit)
    override suspend fun moveFolder(folderId: Long, newParentId: Long?) =
        Result.Success(Unit)
    override suspend fun deleteFolderCascade(folderId: Long) =
        Result.Success(
            BookmarkDescendantCount(0, 0),
        )
}

/** No-op [HistoryRepository]: seeded to Failed, nothing is ever recorded. */
private object OfflineErrorNoopHistoryRepository :
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
