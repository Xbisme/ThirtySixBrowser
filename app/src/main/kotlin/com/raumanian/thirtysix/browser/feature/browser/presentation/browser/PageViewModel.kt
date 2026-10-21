package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.presentation.util.pngWriter
import com.raumanian.thirtysix.browser.core.presentation.util.webpWriter
import com.raumanian.thirtysix.browser.feature.browser.domain.model.CurrentPage
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Turns WebView callbacks into the page's state and writes the live page through to the active tab and history. */
// One handler per WebView callback, plus the tab, history and cache writes they lead to.
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PageViewModel @Inject constructor(
    observeActiveTab: ObserveActiveTabUseCase,
    private val tabs: TabRepository,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val faviconCache: FaviconCache,
    private val screenshotCache: ScreenshotCache,
    private val recordHistoryEntry: RecordHistoryEntryUseCase,
    private val updateHistoryEntryTitle: UpdateHistoryEntryTitleUseCase,
    private val pageStore: CurrentPageRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<PageUiState> = MutableStateFlow(PageUiState())

    val uiState: StateFlow<PageUiState> = _uiState.asStateFlow()

    /** The page state this holder shares with the other browser holders. */
    val currentPage: StateFlow<CurrentPage> = pageStore.page

    private val _loadProgress = MutableStateFlow(0f)

    /**
     * Page-load progress in `[0f, 1f]`, kept out of [uiState] so a progress tick never emits a new
     * page state; the UI reads it only in the progress line's draw phase.
     */
    val loadProgress: StateFlow<Float> = _loadProgress.asStateFlow()

    val activeTab: StateFlow<Tab?> = observeActiveTab()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = null,
        )

    // Starts at 1: there is always at least one tab once the flow emits, so the badge never flashes empty.
    val tabCount: StateFlow<Int> = tabs.observeTabs()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = INITIAL_TAB_COUNT,
        )

    // The navigation that started but is not yet in history: armed by [onLoadStarted], consumed
    // once by [onLoadFinished], cleared by [onLoadFailed] so an error page is never recorded.
    private var pendingHistoryUrl: String? = null

    // The latest history row, so a title that arrives after the load can be patched onto it.
    private var lastRecordedHistoryId: Long = NO_HISTORY_ROW

    private var lastRecordedHistoryUrl: String? = null

    init {
        // A new tab starts without the previous tab's title; the store drops it on a switch.
        activeTab
            .onEach { tab ->
                // Another tab's page has not failed; a kept Failed would stop its saved history being restored.
                if (tab?.id != pageStore.page.value.activeTab?.id) {
                    _uiState.update { it.copy(loadingState = LoadingState.Idle) }
                }
                pageStore.setActiveTab(tab)
                tab?.let { active -> pageStore.setUrl(active.url) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: PageAction) {
        when (action) {
            is PageAction.LoadStarted -> onLoadStarted(action.url)
            is PageAction.ProgressChanged -> onProgressChanged(action.newProgress)
            is PageAction.LoadFinished -> onLoadFinished(action.url)
            is PageAction.LoadFailed -> onLoadFailed(action.reason)
            PageAction.LoadStopped -> onLoadStopped()
            PageAction.Retry -> onRetry()
            is PageAction.UrlChanged -> onUrlChanged(action.url)
            is PageAction.TitleReceived -> onTitleReceived(action.title)
            is PageAction.IconReceived -> onIconReceived(action.url, action.icon, action.isIncognito)
            is PageAction.ScreenshotReady -> onScreenshotReady(action.bitmap, action.tabId, action.isIncognito)
            is PageAction.CanGoBackChanged -> _uiState.update { it.copy(canGoBack = action.canGoBack) }
            is PageAction.CanGoForwardChanged -> _uiState.update { it.copy(canGoForward = action.canGoForward) }
        }
    }

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
        // The engine's own error page reports progress too; the error screen stays until a new load.
        if (_uiState.value.loadingState is LoadingState.Failed) {
            _loadProgress.value = 0f
            return
        }
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
        if (_uiState.value.loadingState is LoadingState.Failed) {
            pageStore.setUrl(url)
            persistActiveTabState(url)
            return
        }
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

    // A stalled server may not start the load for seconds; the indicator shows from the tap.
    private fun onRetry() {
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Failed) {
                current.copy(loadingState = LoadingState.Loading)
            } else {
                current
            }
        }
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

    // Fired from onPageStarted and doUpdateVisitedHistory, so every hop of a redirect chain shows.
    private fun onUrlChanged(url: String) {
        pageStore.setUrl(url)
        persistActiveTabState(url)
    }

    private fun onTitleReceived(title: String) {
        // The engine's error page reports its own title, which is not the page's.
        if (_uiState.value.loadingState is LoadingState.Failed) return
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

    private fun onIconReceived(url: String, icon: Bitmap, isIncognito: Boolean) {
        // Incognito never writes favicons: the cache is keyed by host and would leak browsing into normal tabs.
        if (isIncognito) return
        viewModelScope.launch {
            faviconCache.save(url, icon.pngWriter())
        }
    }

    /** Stores the preview under the tab it was taken from, which may no longer be the active one. */
    private fun onScreenshotReady(bitmap: Bitmap, tabId: Long, isIncognito: Boolean) {
        // Incognito never writes previews to disk, where they could be recovered after the tab closed.
        if (isIncognito) return
        viewModelScope.launch {
            // A preview taken just before its tab closed would outlive the tab on disk.
            if (tabs.observeTabs().first().none { it.id == tabId }) return@launch
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
