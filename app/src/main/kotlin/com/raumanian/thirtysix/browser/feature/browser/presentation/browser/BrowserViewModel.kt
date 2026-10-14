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
import com.raumanian.thirtysix.browser.core.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.core.presentation.util.pngWriter
import com.raumanian.thirtysix.browser.core.presentation.util.webpWriter
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
    private val buildSearchUrl: BuildSearchUrlUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    observeTabs: ObserveTabsUseCase,
    observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val createTab: CreateTabUseCase,
    private val faviconCache: FaviconCache,
    private val screenshotCache: ScreenshotCache,
    private val isUrlBookmarked: IsUrlBookmarkedUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
    private val recordHistoryEntry: RecordHistoryEntryUseCase,
    private val updateHistoryEntryTitle: UpdateHistoryEntryTitleUseCase,
    private val startDownload: StartDownloadUseCase,
) : ViewModel() {

    private val _uiState: MutableStateFlow<BrowserUiState> = MutableStateFlow(
        BrowserUiState(
            currentUrl = defaultHomeUrl,
            loadingState = LoadingState.Idle,
        ),
    )

    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

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
    val tabCount: StateFlow<Int> = observeTabs()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = INITIAL_TAB_COUNT,
        )

    private val currentTitleCache: MutableStateFlow<String> = MutableStateFlow("")

    // In memory only: new tabs and every tab after a process restart start in mobile mode. Tab ids
    // are never reused, so a closed tab's entry is inert.
    private val desktopModeTabs = MutableStateFlow<Set<Long>>(emptySet())

    private val _findInPage = MutableStateFlow(FindInPageState())

    // Kept out of [uiState]: a result per keystroke recomposes only the find bar.
    val findInPage: StateFlow<FindInPageState> = _findInPage.asStateFlow()

    // The navigation that started but is not yet in history: armed by [onLoadStarted], consumed
    // once by [onLoadFinished], cleared by [onLoadFailed] so an error page is never recorded.
    private var pendingHistoryUrl: String? = null

    // The latest history row, so a title that arrives after the load can be patched onto it.
    private var lastRecordedHistoryId: Long = NO_HISTORY_ROW

    private var lastRecordedHistoryUrl: String? = null

    init {
        activeTabFlow
            .onEach { tab ->
                tab?.let { active ->
                    _uiState.update { state -> state.copy(currentUrl = active.url) }
                }
            }
            .launchIn(viewModelScope)

        // A new tab starts without the previous tab's title.
        activeTabFlow
            .map { it?.id }
            .distinctUntilChanged()
            .onEach {
                currentTitleCache.value = ""
                // The find bar belongs to the page it searched.
                onFindClosed()
            }
            .launchIn(viewModelScope)

        combine(activeTabFlow.map { it?.id }, desktopModeTabs) { id, desktop -> id != null && id in desktop }
            .distinctUntilChanged()
            .onEach { desktop -> _uiState.update { it.copy(isDesktopMode = desktop) } }
            .launchIn(viewModelScope)

        observeActiveTabIsIncognito()
            .onEach { incognito ->
                _uiState.update { state -> state.copy(isIncognito = incognito) }
            }
            .launchIn(viewModelScope)

        _uiState
            .map { it.currentUrl }
            .distinctUntilChanged()
            .flatMapLatest { url -> isUrlBookmarked(url) }
            .onEach { bookmarked ->
                _uiState.update { state -> state.copy(isBookmarked = bookmarked) }
            }
            .launchIn(viewModelScope)
    }

    fun onStarTapped() {
        val state = _uiState.value
        val url = state.currentUrl
        val title = currentTitleCache.value
        viewModelScope.launch {
            val result = toggleBookmark(url = url, fallbackTitle = title)
            if (result is Result.Success) {
                val event = when (result.data) {
                    ToggleBookmarkResult.Added -> BookmarkSnackbarEvent.Added
                    ToggleBookmarkResult.Removed -> BookmarkSnackbarEvent.Removed
                }
                _uiState.update { current -> current.copy(bookmarkSnackbarEvent = event) }
            }
        }
    }

    fun consumeBookmarkSnackbarEvent() {
        _uiState.update { it.copy(bookmarkSnackbarEvent = null) }
    }

    // Incognito downloads are recorded like any other; the tab's incognito state is not consulted.
    fun onDownloadRequested(
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
            _uiState.update { current -> current.copy(downloadSnackbarEvent = event) }
        }
    }

    // Called from the permission launcher, the only place that knows the person's answer.
    fun onStoragePermissionDenied(permanently: Boolean) {
        _uiState.update { current ->
            current.copy(
                downloadSnackbarEvent = if (permanently) {
                    DownloadSnackbarEvent.StoragePermissionPermanentlyDenied
                } else {
                    DownloadSnackbarEvent.StoragePermissionDenied
                },
            )
        }
    }

    fun consumeDownloadSnackbarEvent() {
        _uiState.update { it.copy(downloadSnackbarEvent = null) }
    }

    // Read synchronously when a tab's WebView is built, so it starts in the right mode.
    fun isDesktopMode(tabId: Long): Boolean = tabId in desktopModeTabs.value

    /** Flips the active tab's page mode; `null` without an active tab. The caller reloads the WebView. */
    fun onDesktopModeToggled(): Boolean? {
        val tabId = activeTabFlow.value?.id ?: return null
        desktopModeTabs.update { tabs -> if (tabId in tabs) tabs - tabId else tabs + tabId }
        return tabId in desktopModeTabs.value
    }

    fun onFindOpened() {
        _findInPage.value = FindInPageState(isOpen = true)
    }

    fun onFindQueryChanged(query: String) {
        _findInPage.update { state ->
            if (!state.isOpen) {
                state
            } else {
                state.copy(query = query.take(BrowserLimits.MAX_FIND_QUERY_LENGTH), current = 0, total = 0)
            }
        }
    }

    // A late result after the bar closed, or while it is empty, must not reopen or relabel it.
    fun onFindResult(activeOrdinal: Int, total: Int) {
        _findInPage.update { state ->
            when {
                !state.isOpen || state.query.isEmpty() -> state
                total <= 0 -> state.copy(current = 0, total = 0)
                else -> state.copy(current = (activeOrdinal + 1).coerceIn(1, total), total = total)
            }
        }
    }

    fun onFindClosed() {
        _findInPage.value = FindInPageState()
    }

    // [onUrlChanged] owns the address; `url` is kept for symmetry with [onLoadFinished].
    @Suppress("UNUSED_PARAMETER")
    fun onLoadStarted(url: String) {
        pendingHistoryUrl = url
        // Drop the previous page's title so it can never attach to the new URL; [onTitleReceived] patches it in.
        currentTitleCache.value = ""
        // A new page closes the find bar; its highlights went with the old page.
        onFindClosed()
        _loadProgress.value = 0f
        _uiState.update {
            it.copy(loadingState = LoadingState.Loading)
        }
    }

    // Progress goes to [loadProgress]; [uiState] changes only when the loading state itself changes.
    fun onProgressChanged(newProgress: Int) {
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

    fun onLoadFinished(url: String) {
        val previous = _uiState.value
        // One write per started navigation: a platform re-fire of onPageFinished finds the token consumed.
        val shouldRecord = pendingHistoryUrl != null
        pendingHistoryUrl = null
        _loadProgress.value = 0f
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loaded && current.currentUrl == url) {
                current
            } else {
                current.copy(currentUrl = url, loadingState = LoadingState.Loaded)
            }
        }
        persistActiveTabState(url)
        // The use case skips incognito and then returns 0.
        if (shouldRecord) {
            val title = currentTitleCache.value
            val incognito = previous.isIncognito
            viewModelScope.launch {
                val rowId = recordHistoryEntry(url = url, title = title, isIncognito = incognito)
                lastRecordedHistoryId = rowId
                lastRecordedHistoryUrl = url.takeIf { rowId > NO_HISTORY_ROW }
            }
        }
    }

    fun onLoadFailed(reason: ErrorReason) {
        // The platform still delivers onPageFinished for the error page, which must not reach history.
        pendingHistoryUrl = null
        _loadProgress.value = 0f
        _uiState.update { it.copy(loadingState = LoadingState.Failed(reason)) }
    }

    fun onCanGoBackChanged(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    fun onCanGoForwardChanged(canGoForward: Boolean) {
        _uiState.update { it.copy(canGoForward = canGoForward) }
    }

    // The page settles wherever it had reached; the loading indicator hides at once.
    fun onLoadStopped() {
        _loadProgress.value = 0f
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loading) {
                current.copy(loadingState = LoadingState.Loaded)
            } else {
                current
            }
        }
    }

    fun onAddressBarTextChange(newText: String) {
        _uiState.update { it.copy(addressBarText = newText) }
    }

    fun onAddressBarFocusChange(focused: Boolean) {
        _uiState.update { it.copy(isAddressBarFocused = focused) }
    }

    /**
     * Returns `true` when something was submitted, so the caller can release focus and keyboard at
     * once; a search URL is then built and loaded asynchronously.
     */
    fun onAddressBarSubmit(loadUrl: (String) -> Unit): Boolean = submit(_uiState.value.addressBarText, loadUrl)

    /** Shows the Home page over a loaded page; does nothing on Home. */
    fun onHomePressed() {
        _uiState.update { if (it.currentUrl == UrlConstants.HOME_PAGE_URL) it else it.copy(homeOverlay = true) }
    }

    fun onHomeOverlayDismissed() {
        _uiState.update { it.copy(homeOverlay = false) }
    }

    /** Submits like the address bar, then remembers that this navigation started on Home. */
    fun onHomeSubmit(input: String, loadUrl: (String) -> Unit): Boolean {
        val handled = submit(input, loadUrl)
        if (handled) onNavigateFromHome()
        return handled
    }

    fun onNavigateFromHome() {
        _uiState.update { it.copy(homeOverlay = false, cameFromHome = true) }
    }

    /**
     * Back with no WebView history: returns the tab to Home when the page was opened from Home in a
     * normal tab; otherwise `false`, and the system handles Back.
     */
    fun onBackWithoutHistory(): Boolean {
        val state = _uiState.value
        if (!state.cameFromHome || state.isIncognito) return false
        currentTitleCache.value = ""
        _uiState.update {
            it.copy(currentUrl = UrlConstants.HOME_PAGE_URL, cameFromHome = false, homeOverlay = false)
        }
        persistActiveTabState(UrlConstants.HOME_PAGE_URL)
        return true
    }

    private fun submit(raw: String, loadUrl: (String) -> Unit): Boolean =
        when (val classified = classifyAddressBarInput(raw)) {
            AddressBarSubmitResult.Empty -> false
            is AddressBarSubmitResult.Url -> {
                loadUrl(classified.target)
                true
            }
            is AddressBarSubmitResult.Query -> {
                viewModelScope.launch {
                    val searchUrl = buildSearchUrl(classified.text)
                    loadUrl(searchUrl)
                }
                true
            }
        }

    fun onAddressBarClear() {
        _uiState.update { it.copy(addressBarText = "") }
    }

    // Fired from onPageStarted and doUpdateVisitedHistory, so every hop of a redirect chain shows.
    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(currentUrl = url) }
        persistActiveTabState(url)
    }

    fun onTitleReceived(title: String) {
        currentTitleCache.value = title
        // The title usually arrives after the load finished; patch it onto the row just written.
        if (lastRecordedHistoryUrl == _uiState.value.currentUrl) {
            val rowId = lastRecordedHistoryId
            viewModelScope.launch { updateHistoryEntryTitle(rowId, title) }
        }
        val activeId = activeTabFlow.value?.id ?: return
        val incognito = _uiState.value.isIncognito
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(
                tabId = activeId,
                url = _uiState.value.currentUrl,
                title = title,
                isIncognito = incognito,
            )
        }
    }

    /** Opens a new Home tab, or reports that the tab limit is reached. */
    fun onLongPressNewTab() {
        viewModelScope.launch {
            val result = createTab(defaultHomeUrl)
            if (result is Result.Error && result.throwable is MaxTabsReachedException) {
                _uiState.update { it.copy(tabsEvent = TabsErrorEvent.MaxTabsReached) }
            }
        }
    }

    fun consumeTabsEvent() {
        _uiState.update { it.copy(tabsEvent = null) }
    }

    fun onIconReceived(url: String, icon: Bitmap) {
        // Incognito never writes favicons: the cache is keyed by host and would leak browsing into normal tabs.
        if (_uiState.value.isIncognito) return
        viewModelScope.launch {
            faviconCache.save(url, icon.pngWriter())
        }
    }

    /** Stores the tab preview by tab id; dropped when there is no active tab yet. */
    fun onScreenshotReady(bitmap: Bitmap) {
        // Incognito never writes previews to disk, where they could be recovered after the tab closed.
        if (_uiState.value.isIncognito) return
        val tabId = activeTabFlow.value?.id ?: return
        viewModelScope.launch {
            screenshotCache.save(tabId, bitmap.webpWriter())
        }
    }

    private fun persistActiveTabState(url: String) {
        val activeId = activeTabFlow.value?.id ?: return
        val incognito = _uiState.value.isIncognito
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(
                tabId = activeId,
                url = url,
                title = currentTitleCache.value,
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
