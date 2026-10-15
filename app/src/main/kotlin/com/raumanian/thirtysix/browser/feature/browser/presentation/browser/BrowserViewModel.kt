package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.AddressBarSubmitResult
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.classifyAddressBarInput
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.core.presentation.util.pngWriter
import com.raumanian.thirtysix.browser.core.presentation.util.webpWriter
import com.raumanian.thirtysix.browser.feature.browser.domain.model.CurrentPage
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.StartDownloadResult
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.StartDownloadUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkResult
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Turns WebView callbacks into [BrowserUiState] and writes the live page through to the active tab. */
// Many thin state mutators over one source of truth; splitting the view model would split that state.
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
    private val searchEngine: SearchEngineRepository,
    observeActiveTab: ObserveActiveTabUseCase,
    private val tabs: TabRepository,
    observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val faviconCache: FaviconCache,
    private val screenshotCache: ScreenshotCache,
    private val isUrlBookmarked: IsUrlBookmarkedUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
    private val recordHistoryEntry: RecordHistoryEntryUseCase,
    private val updateHistoryEntryTitle: UpdateHistoryEntryTitleUseCase,
    private val startDownload: StartDownloadUseCase,
    private val pageStore: CurrentPageRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<PageUiState> = MutableStateFlow(PageUiState())

    val uiState: StateFlow<PageUiState> = _uiState.asStateFlow()

    /** The page state this holder shares with the other browser holders. */
    val currentPage: StateFlow<CurrentPage> = pageStore.page

    // Kept until the screen collects them, as the consumed-once state fields they replace were.
    private val eventChannel = Channel<BrowserEvent>(Channel.BUFFERED)
    val events: Flow<BrowserEvent> = eventChannel.receiveAsFlow()

    private val _loadProgress = MutableStateFlow(0f)

    /**
     * Page-load progress in `[0f, 1f]`, kept out of [uiState] so a progress tick never emits a new
     * [BrowserUiState]; the UI reads it only in the progress line's draw phase.
     */
    val loadProgress: StateFlow<Float> = _loadProgress.asStateFlow()

    private val activeTabFlow: StateFlow<Tab?> = observeActiveTab()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = null,
        )

    val activeTab: StateFlow<Tab?> = activeTabFlow

    // Starts at 1: there is always at least one tab once the flow emits, so the badge never flashes empty.
    val tabCount: StateFlow<Int> = tabs.observeTabs()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = INITIAL_TAB_COUNT,
        )

    // In memory only: new tabs and every tab after a process restart start in mobile mode. Tab ids
    // are never reused, so a closed tab's entry is inert.
    private val desktopModeTabs = MutableStateFlow<Set<Long>>(emptySet())

    val desktopMode: DesktopModeLookup = DesktopModeLookup { tabId -> tabId in desktopModeTabs.value }

    // The navigation that started but is not yet in history: armed by [onLoadStarted], consumed
    // once by [onLoadFinished], cleared by [onLoadFailed] so an error page is never recorded.
    private var pendingHistoryUrl: String? = null

    // The latest history row, so a title that arrives after the load can be patched onto it.
    private var lastRecordedHistoryId: Long = NO_HISTORY_ROW

    private var lastRecordedHistoryUrl: String? = null

    init {
        // A new tab starts without the previous tab's title; the store drops it on a switch.
        activeTabFlow
            .onEach { tab ->
                pageStore.setActiveTab(tab)
                tab?.let { active -> pageStore.setUrl(active.url) }
            }
            .launchIn(viewModelScope)

        combine(pageStore.page.map { it.activeTab?.id }, desktopModeTabs) { id, desktop -> id != null && id in desktop }
            .distinctUntilChanged()
            .onEach { desktop -> _uiState.update { it.copy(isDesktopMode = desktop) } }
            .launchIn(viewModelScope)

        observeActiveTabIsIncognito()
            .onEach { incognito -> pageStore.setIncognito(incognito) }
            .launchIn(viewModelScope)

        pageStore.page
            .map { it.url }
            .distinctUntilChanged()
            .flatMapLatest { url -> isUrlBookmarked(url) }
            .onEach { bookmarked ->
                _uiState.update { state -> state.copy(isBookmarked = bookmarked) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: BrowserAction) {
        when (action) {
            is BrowserAction.Page -> onPage(action)
            is BrowserAction.AddressBar -> onAddressBar(action)
            is BrowserAction.Home -> onHome(action)
            is BrowserAction.Download -> onDownload(action)
            BrowserAction.StarTapped -> onStarTapped()
            BrowserAction.ToggleDesktopMode -> onDesktopModeToggled()
            BrowserAction.BackWithoutHistory -> onBackWithoutHistory()
            BrowserAction.LongPressNewTab -> onLongPressNewTab()
        }
    }

    private fun onPage(action: BrowserAction.Page) {
        when (action) {
            is BrowserAction.Page.LoadStarted -> onLoadStarted(action.url)
            is BrowserAction.Page.ProgressChanged -> onProgressChanged(action.newProgress)
            is BrowserAction.Page.LoadFinished -> onLoadFinished(action.url)
            is BrowserAction.Page.LoadFailed -> onLoadFailed(action.reason)
            BrowserAction.Page.LoadStopped -> onLoadStopped()
            is BrowserAction.Page.UrlChanged -> onUrlChanged(action.url)
            is BrowserAction.Page.TitleReceived -> onTitleReceived(action.title)
            is BrowserAction.Page.IconReceived -> onIconReceived(action.url, action.icon)
            is BrowserAction.Page.ScreenshotReady -> onScreenshotReady(action.bitmap)
            is BrowserAction.Page.CanGoBackChanged -> _uiState.update { it.copy(canGoBack = action.canGoBack) }
            is BrowserAction.Page.CanGoForwardChanged -> _uiState.update { it.copy(canGoForward = action.canGoForward) }
        }
    }

    private fun onAddressBar(action: BrowserAction.AddressBar) {
        when (action) {
            is BrowserAction.AddressBar.TextChange -> _uiState.update { it.copy(addressBarText = action.text) }
            is BrowserAction.AddressBar.FocusChange -> _uiState.update { it.copy(isAddressBarFocused = action.focused) }
            BrowserAction.AddressBar.Submit -> submit(_uiState.value.addressBarText)
            BrowserAction.AddressBar.Clear -> _uiState.update { it.copy(addressBarText = "") }
        }
    }

    private fun onHome(action: BrowserAction.Home) {
        when (action) {
            BrowserAction.Home.Pressed -> onHomePressed()
            BrowserAction.Home.OverlayDismissed -> _uiState.update { it.copy(homeOverlay = false) }
            is BrowserAction.Home.Submit -> onHomeSubmit(action.input)
            BrowserAction.Home.NavigateFrom -> onNavigateFromHome()
        }
    }

    private fun onDownload(action: BrowserAction.Download) {
        when (action) {
            is BrowserAction.Download.Requested ->
                onDownloadRequested(action.url, action.userAgent, action.contentDisposition, action.mimeType)
            is BrowserAction.Download.StoragePermissionDenied -> onStoragePermissionDenied(action.permanently)
        }
    }

    private fun onStarTapped() {
        val page = pageStore.page.value
        val url = page.url
        val title = page.title
        viewModelScope.launch {
            val result = toggleBookmark(url = url, fallbackTitle = title)
            if (result is Result.Success) {
                val event = when (result.data) {
                    ToggleBookmarkResult.Added -> BookmarkSnackbarEvent.Added
                    ToggleBookmarkResult.Removed -> BookmarkSnackbarEvent.Removed
                }
                eventChannel.trySend(BrowserEvent.Bookmark(event))
            }
        }
    }

    // Incognito downloads are recorded like any other; the tab's incognito state is not consulted.
    private fun onDownloadRequested(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        viewModelScope.launch {
            val event = when (val result = startDownload(url, contentDisposition, mimeType, userAgent)) {
                is StartDownloadResult.Started -> DownloadSnackbarEvent.Started(result.fileName)
                StartDownloadResult.ServiceUnavailable -> DownloadSnackbarEvent.ServiceUnavailable
            }
            eventChannel.trySend(BrowserEvent.Download(event))
        }
    }

    // Called from the permission launcher, the only place that knows the person's answer.
    private fun onStoragePermissionDenied(permanently: Boolean) {
        val event = if (permanently) {
            DownloadSnackbarEvent.StoragePermissionPermanentlyDenied
        } else {
            DownloadSnackbarEvent.StoragePermissionDenied
        }
        eventChannel.trySend(BrowserEvent.Download(event))
    }

    /** Flips the active tab's page mode; nothing without an active tab. The screen reloads the WebView. */
    private fun onDesktopModeToggled() {
        val tabId = pageStore.page.value.activeTab?.id ?: return
        desktopModeTabs.update { desktop -> if (tabId in desktop) desktop - tabId else desktop + tabId }
        eventChannel.trySend(BrowserEvent.SetDesktopMode(tabId in desktopModeTabs.value))
    }

    // [onUrlChanged] owns the address; `url` is kept for symmetry with [onLoadFinished].
    @Suppress("UNUSED_PARAMETER")
    private fun onLoadStarted(url: String) {
        pendingHistoryUrl = url
        // Drops the previous page's title so it can never attach to the new URL ([onTitleReceived]
        // patches it in) and closes the find bar, whose highlights went with the old page.
        pageStore.startNewPage()
        _loadProgress.value = 0f
        _uiState.update {
            it.copy(loadingState = LoadingState.Loading)
        }
    }

    // Progress goes to [loadProgress]; [uiState] changes only when the loading state itself changes.
    private fun onProgressChanged(newProgress: Int) {
        val clamped = newProgress.coerceIn(0, MAX_PROGRESS)
        if (clamped >= MAX_PROGRESS) {
            _loadProgress.value = 0f
            _uiState.update { current ->
                if (current.loadingState is LoadingState.Loaded) {
                    current
                } else {
                    current.copy(loadingState = LoadingState.Loaded)
                }
            }
        } else {
            _loadProgress.value = clamped / MAX_PROGRESS_F
            _uiState.update { current ->
                if (current.loadingState is LoadingState.Loading) {
                    current
                } else {
                    current.copy(loadingState = LoadingState.Loading)
                }
            }
        }
    }

    private fun onLoadFinished(url: String) {
        val previousIncognito = pageStore.page.value.isIncognito
        // One write per started navigation: a platform re-fire of onPageFinished finds the token consumed.
        val shouldRecord = pendingHistoryUrl != null
        pendingHistoryUrl = null
        _loadProgress.value = 0f
        if (_uiState.value.loadingState !is LoadingState.Loaded || pageStore.page.value.url != url) {
            pageStore.setUrl(url)
            _uiState.update { it.copy(loadingState = LoadingState.Loaded) }
        }
        persistActiveTabState(url)
        // The use case skips incognito and then returns 0.
        if (shouldRecord) {
            val title = pageStore.page.value.title
            val incognito = previousIncognito
            viewModelScope.launch {
                val rowId = recordHistoryEntry(url = url, title = title, isIncognito = incognito)
                lastRecordedHistoryId = rowId
                lastRecordedHistoryUrl = url.takeIf { rowId > NO_HISTORY_ROW }
            }
        }
    }

    private fun onLoadFailed(reason: ErrorReason) {
        // The platform still delivers onPageFinished for the error page, which must not reach history.
        pendingHistoryUrl = null
        _loadProgress.value = 0f
        _uiState.update { it.copy(loadingState = LoadingState.Failed(reason)) }
    }

    // The page settles wherever it had reached; the loading indicator hides at once.
    private fun onLoadStopped() {
        _loadProgress.value = 0f
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loading) {
                current.copy(loadingState = LoadingState.Loaded)
            } else {
                current
            }
        }
    }

    /** Shows the Home page over a loaded page; does nothing on Home. */
    private fun onHomePressed() {
        if (pageStore.page.value.url != UrlConstants.HOME_PAGE_URL) _uiState.update { it.copy(homeOverlay = true) }
    }

    /** Submits like the address bar, then remembers that this navigation started on Home. */
    private fun onHomeSubmit(input: String) {
        if (submit(input)) onNavigateFromHome()
    }

    private fun onNavigateFromHome() {
        _uiState.update { it.copy(homeOverlay = false, cameFromHome = true) }
    }

    /** Back with no WebView history: returns the tab to Home when the page was opened from Home in a normal tab. */
    private fun onBackWithoutHistory() {
        if (!_uiState.value.cameFromHome || pageStore.page.value.isIncognito) return
        pageStore.setTitle("")
        pageStore.setUrl(UrlConstants.HOME_PAGE_URL)
        _uiState.update { it.copy(cameFromHome = false, homeOverlay = false) }
        persistActiveTabState(UrlConstants.HOME_PAGE_URL)
    }

    // True when something was submitted; a search URL is then built and loaded asynchronously.
    private fun submit(raw: String): Boolean =
        when (val classified = classifyAddressBarInput(raw)) {
            AddressBarSubmitResult.Empty -> false
            is AddressBarSubmitResult.Url -> {
                eventChannel.trySend(BrowserEvent.LoadUrl(classified.target))
                true
            }
            is AddressBarSubmitResult.Query -> {
                viewModelScope.launch {
                    eventChannel.trySend(BrowserEvent.LoadUrl(searchEngine.buildSearchUrl(classified.text)))
                }
                true
            }
        }

    // Fired from onPageStarted and doUpdateVisitedHistory, so every hop of a redirect chain shows.
    private fun onUrlChanged(url: String) {
        pageStore.setUrl(url)
        persistActiveTabState(url)
    }

    private fun onTitleReceived(title: String) {
        pageStore.setTitle(title)
        val page = pageStore.page.value
        // The title usually arrives after the load finished; patch it onto the row just written.
        if (lastRecordedHistoryUrl == page.url) {
            val rowId = lastRecordedHistoryId
            viewModelScope.launch { updateHistoryEntryTitle(rowId, title) }
        }
        val activeId = page.activeTab?.id ?: return
        val incognito = page.isIncognito
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(
                tabId = activeId,
                url = pageStore.page.value.url,
                title = title,
                isIncognito = incognito,
            )
        }
    }

    /** Opens a new Home tab, or reports that the tab limit is reached. */
    private fun onLongPressNewTab() {
        viewModelScope.launch {
            val result = tabs.createTab(defaultHomeUrl)
            if (result is Result.Error && result.throwable is MaxTabsReachedException) {
                eventChannel.trySend(BrowserEvent.TabLimit(TabsErrorEvent.MaxTabsReached))
            }
        }
    }

    private fun onIconReceived(url: String, icon: Bitmap) {
        // Incognito never writes favicons: the cache is keyed by host and would leak browsing into normal tabs.
        if (pageStore.page.value.isIncognito) return
        viewModelScope.launch {
            faviconCache.save(url, icon.pngWriter())
        }
    }

    /** Stores the tab preview by tab id; dropped when there is no active tab yet. */
    private fun onScreenshotReady(bitmap: Bitmap) {
        // Incognito never writes previews to disk, where they could be recovered after the tab closed.
        val page = pageStore.page.value
        if (page.isIncognito) return
        val tabId = page.activeTab?.id ?: return
        viewModelScope.launch {
            screenshotCache.save(tabId, bitmap.webpWriter())
        }
    }

    private fun persistActiveTabState(url: String) {
        val page = pageStore.page.value
        val activeId = page.activeTab?.id ?: return
        val incognito = page.isIncognito
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(
                tabId = activeId,
                url = url,
                title = page.title,
                isIncognito = incognito,
            )
        }
    }

    private companion object {
        const val NO_HISTORY_ROW: Long = 0L

        const val MAX_PROGRESS: Int = 100
        const val MAX_PROGRESS_F: Float = 100f
        const val SUBSCRIBE_TIMEOUT_MS: Long = 5_000L
        const val INITIAL_TAB_COUNT: Int = 1
    }
}
