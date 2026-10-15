package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.Locator
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
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
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
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Browser screen assertions that need a real page load: the page renders, and a configuration
 * change keeps `currentUrl`. Render branches that must not race a live load are tested with a
 * seeded view model in `BrowserScreenLoadingIndicatorTest` and `BrowserScreenOfflineErrorTest`.
 */
@HiltAndroidTest
// The browser screen reads settings (text size), so each test gets its own DataStore file:
// a fresh Hilt component per test would otherwise open a second one ("multiple DataStores active").
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenInstrumentedTest {

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
            // Built by hand with a fixed example.com URL and no-op repositories; class-scoped so the
            // rotation test can re-attach the same view model after the activity is recreated.
            val noopTabRepo = InstrumentedNoopTabRepository
            val noopIncognitoRepo = InstrumentedNoopIncognitoTabRepository
            val observeAllTabs = ObserveAllTabsUseCase(noopTabRepo, noopIncognitoRepo)
            viewModel = BrowserViewModel(
                defaultHomeUrl = TEST_PAGE_URL,
                searchEngine = InstrumentedNoopSearchEngineRepository,
                observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
                tabs = noopTabRepo,
                observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
                updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(noopTabRepo, noopIncognitoRepo),
                faviconCache = InstrumentedNoopFaviconCache,
                screenshotCache = InstrumentedNoopScreenshotCache,
                isUrlBookmarked = IsUrlBookmarkedUseCase(InstrumentedNoopBookmarkRepository),
                toggleBookmark = ToggleBookmarkUseCase(
                    InstrumentedNoopBookmarkRepository,
                    AddBookmarkUseCase(InstrumentedNoopBookmarkRepository),
                ),
                recordHistoryEntry = RecordHistoryEntryUseCase(
                    InstrumentedNoopHistoryRepository,
                ),
                updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(InstrumentedNoopHistoryRepository),
                startDownload = instrumentedNoOpStartDownloadUseCase(),
                pageStore = CurrentPageRepositoryImpl(TEST_PAGE_URL),
            )
            composeRule.activity.setContent {
                BrowserRoute(
                    slots = NoSlots,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun pageRenders_assertsDomContainsExampleDomain() {
        // The Espresso-Web atom waits for the JS-ready state, then checks the page text.
        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "body"))
            .check(webMatches(getText(), containsString(PAGE_TEXT)))
    }

    @Test
    fun rotation_preservesCurrentUrlInUiState() {
        // Configuration change: the same view model is re-attached, so the same URL renders again.
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                BrowserRoute(
                    slots = NoSlots,
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()

        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "body"))
            .check(webMatches(getText(), containsString(PAGE_TEXT)))
    }

    private companion object {
        // Pinned to example.com for the page-text assertion, independent of the app's defaults.
        const val TEST_PAGE_URL: String = "https://example.com"

        // example.com has no <h1>Example Domain</h1>, so the check reads body text.
        const val PAGE_TEXT: String = "documentation examples"

        // A CI emulator budget, not the production loading target.
    }
}

/** No-op [SearchEngineRepository]: the query branch is never reached here. */
private object InstrumentedNoopSearchEngineRepository : SearchEngineRepository {
    override suspend fun buildSearchUrl(query: String): String =
        error("instrumented test should not reach the search-URL build path")
}

/** No-op [TabRepository]: no active tab, so the view model's collectors leave state alone. */
private object InstrumentedNoopTabRepository : TabRepository {
    // Hot, never completing: `ObserveAllTabsUseCase` combines upstreams that must stay subscribed.
    private val state: kotlinx.coroutines.flow.MutableStateFlow<List<Tab>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("instrumented test should not reach createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAllTabs() = Unit
    override suspend fun getTabCount(): Int = 0
}

/** No-op [IncognitoTabRepository]; hot for the same reason as [InstrumentedNoopTabRepository]. */
private object InstrumentedNoopIncognitoTabRepository : IncognitoTabRepository {
    private val state: kotlinx.coroutines.flow.MutableStateFlow<List<Tab>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("instrumented test should not reach incognito createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAll() = Unit
    override suspend fun getCount(): Int = 0
}

/** No-op [FaviconCache]: icons may arrive while the page loads, but nothing is asserted on them. */
private object InstrumentedNoopFaviconCache : FaviconCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun retainOnly(hosts: Set<String>) = Unit
    override fun fileFor(url: String): File? = null
}

/** No-op [ScreenshotCache]. */
private object InstrumentedNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}

/** No-op bookmark repository; the star icon has its own test. */
private object InstrumentedNoopBookmarkRepository :
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
    override suspend fun addBookmark(bookmark: Bookmark) = Result.Success(0L)
    override suspend fun updateBookmark(bookmark: Bookmark) = Result.Success(Unit)
    override suspend fun deleteBookmark(id: Long) = Result.Success(Unit)
    override suspend fun deleteMostRecentBookmarkByUrl(url: String) = Result.Success(0)
    override suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?) = Result.Success(Unit)
    override fun observeFoldersByParent(parentId: Long?) =
        kotlinx.coroutines.flow.flowOf(emptyList<BookmarkFolder>())
    override fun observeAllFolders() =
        kotlinx.coroutines.flow.flowOf(emptyList<BookmarkFolder>())
    override suspend fun getFolder(id: Long) = null
    override suspend fun getAncestorChain(folderId: Long) = emptyList<BookmarkFolder>()
    override suspend fun countDescendants(folderId: Long) =
        BookmarkDescendantCount(0, 0)
    override suspend fun createFolder(name: String, parentId: Long?) = Result.Success(0L)
    override suspend fun renameFolder(folderId: Long, newName: String) = Result.Success(Unit)
    override suspend fun moveFolder(folderId: Long, newParentId: Long?) = Result.Success(Unit)
    override suspend fun deleteFolderCascade(folderId: Long) =
        Result.Success(BookmarkDescendantCount(0, 0))
}

/** No-op [HistoryRepository]. */
private object InstrumentedNoopHistoryRepository :
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

private val NoSlots = BrowserSlots(
    fireButton = FireButton(enabled = true, onClick = {}),
    fireOverlay = {},
    quickSettings = { _, _, _ -> },
)
