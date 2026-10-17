package com.raumanian.thirtysix.browser.feature.browser.fixture

import androidx.compose.runtime.Composable
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.model.ImportOutcome
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
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.AddressBarViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserRoute
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserSlots
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.FindInPageViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.PageActionsViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.PageViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.rememberBrowserPageHolders
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The browser holders that share the page store, built over one store and no-op repositories
 * as `BrowserRoute` gets them from Hilt. Holders without the store still come from Hilt.
 */
class BrowserScreenHolders(startUrl: String) {
    private val pageStore = CurrentPageRepositoryImpl(startUrl)
    private val observeAllTabs = ObserveAllTabsUseCase(NoopTabRepository, NoopIncognitoTabRepository)
    private val updateActiveTab = UpdateActiveTabUrlAndTitleUseCase(NoopTabRepository, NoopIncognitoTabRepository)

    val page = PageViewModel(
        observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
        tabs = NoopTabRepository,
        observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
        updateActiveTabUrlAndTitle = updateActiveTab,
        faviconCache = NoopFaviconCache,
        screenshotCache = NoopScreenshotCache,
        recordHistoryEntry = RecordHistoryEntryUseCase(NoopHistoryRepository),
        updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(NoopHistoryRepository),
        pageStore = pageStore,
    )

    val addressBar = AddressBarViewModel(
        pageStore = pageStore,
        searchEngine = EchoSearchEngine,
        updateActiveTabUrlAndTitle = updateActiveTab,
    )

    val findInPage = FindInPageViewModel(pageStore)

    val pageActions = PageActionsViewModel(
        defaultHomeUrl = UrlConstants.HOME_PAGE_URL,
        pageStore = pageStore,
        tabs = NoopTabRepository,
        isUrlBookmarked = IsUrlBookmarkedUseCase(NoopBookmarkRepository),
        toggleBookmark = ToggleBookmarkUseCase(NoopBookmarkRepository, AddBookmarkUseCase(NoopBookmarkRepository)),
    )

    @Composable
    fun Route() {
        BrowserRoute(
            slots = NoSlots,
            holders = rememberBrowserPageHolders(
                page = page,
                addressBar = addressBar,
                findInPage = findInPage,
                pageActions = pageActions,
            ),
        )
    }
}

private object EchoSearchEngine : SearchEngineRepository {
    override suspend fun buildSearchUrl(query: String): String = query
}

/** No active tab, so the page holder's collectors leave the seeded state alone. */
private object NoopTabRepository : TabRepository {
    // Hot, never completing: `ObserveAllTabsUseCase` combines upstreams that must stay subscribed.
    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("browser screen tests never create a tab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAllTabs() = Unit
    override suspend fun getTabCount(): Int = 0
}

private object NoopIncognitoTabRepository : IncognitoTabRepository {
    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state
    override suspend fun createTab(url: String): Result<Tab> =
        error("browser screen tests never create a tab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAll() = Unit
    override suspend fun getCount(): Int = 0
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

private object NoopBookmarkRepository :
    BookmarkRepository {
    override fun observeBookmarksByFolder(folderId: Long?) =
        flowOf(emptyList<Bookmark>())
    override fun observeBookmarksByUrl(url: String) =
        flowOf(emptyList<Bookmark>())
    override fun observeRecentBookmarks(limit: Int) =
        flowOf(emptyList<Bookmark>())
    override suspend fun getAllBookmarks() = emptyList<Bookmark>()

    override fun searchBookmarks(query: String) =
        flowOf(emptyList<Bookmark>())
    override suspend fun getBookmark(id: Long) = null
    override suspend fun countBookmarksByUrl(url: String) = 0
    override suspend fun addBookmark(bookmark: Bookmark) = Result.Success(0L)
    override suspend fun updateBookmark(bookmark: Bookmark) = Result.Success(Unit)
    override suspend fun deleteBookmark(id: Long) = Result.Success(Unit)
    override suspend fun deleteMostRecentBookmarkByUrl(url: String) = Result.Success(0)
    override suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?) = Result.Success(Unit)
    override fun observeFoldersByParent(parentId: Long?) =
        flowOf(emptyList<BookmarkFolder>())
    override fun observeAllFolders() =
        flowOf(emptyList<BookmarkFolder>())
    override suspend fun getFolder(id: Long) = null
    override suspend fun getAncestorChain(folderId: Long) = emptyList<BookmarkFolder>()
    override suspend fun countDescendants(folderId: Long) =
        BookmarkDescendantCount(0, 0)
    override suspend fun createFolder(name: String, parentId: Long?) = Result.Success(0L)
    override suspend fun renameFolder(folderId: Long, newName: String) = Result.Success(Unit)
    override suspend fun moveFolder(folderId: Long, newParentId: Long?) = Result.Success(Unit)
    override suspend fun deleteFolderCascade(folderId: Long) =
        Result.Success(BookmarkDescendantCount(0, 0))
    override suspend fun countBookmarks() = 0
    override suspend fun countFolders() = 0
    override suspend fun getBookmarkTree() = BookmarkTreeNode.Folder("", emptyList())
    override suspend fun importTree(rootName: String, tree: BookmarkTreeNode.Folder) =
        Result.Success(ImportOutcome(0L, rootName, 0, 0))
}

private object NoopHistoryRepository :
    HistoryRepository {
    override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long = 0L
    override fun observeAll() =
        flowOf(emptyList<HistoryEntry>())
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
