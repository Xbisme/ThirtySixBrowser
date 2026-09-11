package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.StartDownloadResult
import com.raumanian.thirtysix.browser.domain.usecase.StartDownloadUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ToggleBookmarkResult
import com.raumanian.thirtysix.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.presentation.tabs.TabsErrorEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Spec 007 — owns [BrowserUiState] and translates WebView client/chrome
 * callbacks into state transitions.
 *
 * Spec 008 extension (canGoBack/canGoForward + Stop + homeUrl getter).
 * Spec 009 extension (address-bar text/focus + onAddressBarSubmit + classifier).
 * Spec 010 extension (BuildSearchUrlUseCase Query branch via viewModelScope).
 *
 * Spec 011 extension (T034 / R7):
 * - Constructor gains 4 new use-case injections: [observeActiveTab],
 *   [observeTabs] (powers [tabCount]), [updateActiveTabUrlAndTitle],
 *   [createTab].
 * - `init {}` block collects the active-tab Flow to seed
 *   `_uiState.currentUrl` from the active tab on cold start AND on every
 *   subsequent tab switch (R7). A second collector resets the cached
 *   [currentTitleCache] whenever the active-tab id changes so a stale title from
 *   a previous tab does not leak into the next tab's first write-through.
 * - New methods: [onLongPressNewTab] (Q2 long-press on the 5th BottomAppBar
 *   button), [onTitleReceived] (M1 — fires from `WebChromeClient.onReceivedTitle`
 *   via `BrowserNavigationCallbacks.onTitleChange`), [consumeTabsEvent].
 * - Modified methods: [onUrlChanged] / [onLoadFinished] now write through to
 *   `TabRepository` via [updateActiveTabUrlAndTitle] so process-death
 *   restoration recovers the live URL trace (FR-022).
 *
 * The default URL is Hilt-injected (production binding in
 * [com.raumanian.thirtysix.browser.di.UrlConfigModule]). Instrumented tests
 * construct this ViewModel directly with explicit URL + use-case parameters.
 */
// 16 callback methods (load lifecycle + navigation + address bar + tabs); each
// is a thin state-mutator (`_uiState.update { ... }`) with no business logic.
// Splitting into smaller ViewModels is premature — every method shares the
// same single source of truth (`BrowserUiState`).
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

    private val activeTabFlow: StateFlow<Tab?> = observeActiveTab()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = null,
        )

    /** Active tab snapshot exposed to the Composable for `LaunchedEffect(activeTab.id)`. */
    val activeTab: StateFlow<Tab?> = activeTabFlow

    /**
     * Spec 011 — number of currently-open tabs. Drives the BadgedBox count
     * over the 5th BottomAppBar button (FR-015). Initial value `1` because
     * R5 guarantees ≥ 1 tab once the Flow has emitted — avoids an empty-
     * badge flash on cold start.
     */
    val tabCount: StateFlow<Int> = observeTabs()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = INITIAL_TAB_COUNT,
        )

    private val currentTitleCache: MutableStateFlow<String> = MutableStateFlow("")

    /**
     * Spec 014 FR-001 / FR-003 — URL of a navigation that has started but has not yet
     * been written to history. Set by [onLoadStarted], consumed by [onLoadFinished],
     * and cleared by [onLoadFailed] so an error page is never recorded.
     *
     * This replaces an earlier `(loadingState == Loaded && currentUrl == url)` heuristic
     * that tried to infer "is this an idempotent re-fire?" from UI state. That heuristic
     * was wrong against the platform's real ordering: `onProgressChanged(100)` moves the
     * state to `Loaded` and `doUpdateVisitedHistory` publishes the new URL *before*
     * `onPageFinished` arrives, so the check matched on genuine navigations and
     * suppressed almost every write. An explicit per-navigation token cannot drift.
     */
    private var pendingHistoryUrl: String? = null

    /** Row id of the most recent history write, so a late title can be patched onto it. */
    private var lastRecordedHistoryId: Long = NO_HISTORY_ROW

    /** URL that [lastRecordedHistoryId] belongs to — guards against patching a stale row. */
    private var lastRecordedHistoryUrl: String? = null

    /**
     * Spec 008 — exposed for the Home affordance + `WebViewActionsHandle.loadHome`
     * so the UI layer doesn't need its own Hilt injection of the URL constant.
     */
    val homeUrl: String get() = defaultHomeUrl

    init {
        // Seed currentUrl from the active tab when present; falls back to
        // defaultHomeUrl during the brief moment before the first emission.
        activeTabFlow
            .onEach { tab ->
                tab?.let { active ->
                    _uiState.update { state -> state.copy(currentUrl = active.url) }
                }
            }
            .launchIn(viewModelScope)

        // Reset cached title on tab switch so a stale title from a previous
        // tab does not leak into the next tab's first write-through (M1).
        activeTabFlow
            .map { it?.id }
            .distinctUntilChanged()
            .onEach { currentTitleCache.value = "" }
            .launchIn(viewModelScope)

        // Spec 012 — observe active-tab incognito state into UiState. Drives
        // the address-bar indicator (FR-015), cache-write gates (FR-009 /
        // FR-010), and the WebView lockdown branch (research.md R5).
        observeActiveTabIsIncognito()
            .onEach { incognito ->
                _uiState.update { state -> state.copy(isIncognito = incognito) }
            }
            .launchIn(viewModelScope)

        // Spec 013 — observe whether the current URL has any bookmark, so the
        // star icon reflects the canonical "any exists" state per FR-002.
        // `flatMapLatest` cancels the previous URL's Flow when the URL changes.
        _uiState
            .map { it.currentUrl }
            .distinctUntilChanged()
            .flatMapLatest { url -> isUrlBookmarked(url) }
            .onEach { bookmarked ->
                _uiState.update { state -> state.copy(isBookmarked = bookmarked) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Spec 013 FR-001..003 — toggle bookmark for the currently displayed URL.
     * Surfaces a [BookmarkSnackbarEvent] so the SnackbarHost can render
     * "Bookmark saved" / "Bookmark removed" once.
     *
     * The current page title (cached via `onTitleChange`) is used as the
     * fallback bookmark title; if the title is blank, [AddBookmarkUseCase]
     * substitutes the URL string per FR-007.
     */
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

    /**
     * Spec 013 — clears the snackbar event after the SnackbarHost has shown
     * the message. Idempotent.
     */
    fun consumeBookmarkSnackbarEvent() {
        _uiState.update { it.copy(bookmarkSnackbarEvent = null) }
    }

    /**
     * Spec 015 FR-001 / FR-002 — the web engine handed back a resource it will not render.
     *
     * Hands it straight to [StartDownloadUseCase], which sanitises the filename, asks the
     * platform, and only then records it. Nothing about the active tab's incognito state is
     * consulted: FR-014a records incognito downloads identically.
     */
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

    /**
     * Spec 015 FR-012 — surface the outcome when the storage permission blocks a download
     * before it can start. Called from the permission launcher, which is the only place
     * that knows the user's answer.
     */
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

    /** Spec 015 — clears the download snackbar event once shown. Idempotent. */
    fun consumeDownloadSnackbarEvent() {
        _uiState.update { it.copy(downloadSnackbarEvent = null) }
    }

    /**
     * Called from `BrowserWebViewClient.onPageStarted`.
     *
     * Spec 009 narrows the responsibility: this method no longer mutates
     * [BrowserUiState.currentUrl]. The Spec 009 [onUrlChanged] is the single
     * source of truth for `currentUrl` mutation; in production, `onPageStarted`
     * fires `onUrlChange` BEFORE `onLoadStarted` so the URL is already up to
     * date when this callback runs. The `url` parameter is retained on the
     * signature for binary-compatibility with existing call sites and to
     * preserve symmetry with [onLoadFinished].
     */
    @Suppress("UNUSED_PARAMETER")
    fun onLoadStarted(url: String) {
        // Spec 014 FR-001 — arm the recorder for exactly this navigation.
        pendingHistoryUrl = url
        // Spec 014 FR-001 — drop the previous page's title so it can never be attached
        // to a different URL. An empty title is safe: the history row and the tab card
        // both fall back to the hostname (FR-009), and [onTitleReceived] patches the
        // real title in as soon as the page announces one.
        currentTitleCache.value = ""
        _uiState.update {
            it.copy(loadingState = LoadingState.Loading(progress = 0f))
        }
    }

    /**
     * Called from `BrowserChromeClient.onProgressChanged`. [newProgress] is
     * 0..100 from WebView; clamped defensively. Progress 100 transitions to
     * [LoadingState.Loaded] (idempotent with [onLoadFinished]).
     */
    fun onProgressChanged(newProgress: Int) {
        val clamped = newProgress.coerceIn(0, MAX_PROGRESS)
        _uiState.update { current ->
            if (clamped >= MAX_PROGRESS) {
                if (current.loadingState is LoadingState.Loaded) {
                    current
                } else {
                    current.copy(loadingState = LoadingState.Loaded)
                }
            } else {
                current.copy(loadingState = LoadingState.Loading(progress = clamped / MAX_PROGRESS_F))
            }
        }
    }

    /**
     * Called from `BrowserWebViewClient.onPageFinished`. Idempotent vs progress(100).
     *
     * Spec 011 — write-through to `TabRepository` so the active tab's
     * persisted URL + (cached) title stay current for process-death
     * restoration (FR-022).
     */
    fun onLoadFinished(url: String) {
        val previous = _uiState.value
        // Spec 014 FR-001 / FR-004 — one write per started navigation. A platform
        // re-fire of `onPageFinished` for the same navigation finds the token already
        // consumed; a genuine revisit re-arms it through [onLoadStarted].
        val shouldRecord = pendingHistoryUrl != null
        pendingHistoryUrl = null
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loaded && current.currentUrl == url) {
                current
            } else {
                current.copy(currentUrl = url, loadingState = LoadingState.Loaded)
            }
        }
        persistActiveTabState(url)
        // Spec 014 FR-001 / FR-002 — record one history entry per genuine page-finish.
        // The use case suppresses incognito internally and returns 0L when it does.
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

    /**
     * Called from `BrowserWebViewClient` error overrides for main-frame failures
     * (US3 / T031). Sub-frame errors are filtered upstream before reaching here.
     */
    fun onLoadFailed(reason: ErrorReason) {
        // Spec 014 FR-003 — a main-frame failure must never reach history, even though
        // the platform still delivers `onPageFinished` for the error page afterwards.
        pendingHistoryUrl = null
        _uiState.update { it.copy(loadingState = LoadingState.Failed(reason)) }
    }

    /**
     * Spec 008 — called from `BrowserWebViewClient.doUpdateVisitedHistory`
     * after every history-mutating commit (page nav, back/forward, fragment
     * change, History API push). Source of truth: `WebView.canGoBack()`.
     */
    fun onCanGoBackChanged(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    /**
     * Spec 008 — symmetric to [onCanGoBackChanged]. Source of truth:
     * `WebView.canGoForward()`.
     */
    fun onCanGoForwardChanged(canGoForward: Boolean) {
        _uiState.update { it.copy(canGoForward = canGoForward) }
    }

    /**
     * Spec 008 — called from the bottom-bar Reload/Stop click handler when the
     * current state is [LoadingState.Loading]. Transitions to [LoadingState.Loaded]
     * so the loading indicator hides immediately (FR-006: page settles at
     * whatever state it had reached). Idempotent; non-Loading states pass
     * through unchanged.
     */
    fun onLoadStopped() {
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loading) {
                current.copy(loadingState = LoadingState.Loaded)
            } else {
                current
            }
        }
    }

    // ---------- Spec 009 — Address Bar / Omnibox ----------

    /** Update the address-bar text on every keystroke from the Composable. */
    fun onAddressBarTextChange(newText: String) {
        _uiState.update { it.copy(addressBarText = newText) }
    }

    /** Update the address-bar focus flag from the Composable's focus listener. */
    fun onAddressBarFocusChange(focused: Boolean) {
        _uiState.update { it.copy(isAddressBarFocused = focused) }
    }

    /**
     * Spec 009 — classify the current [BrowserUiState.addressBarText] and
     * dispatch to [loadUrl] (the imperative `WebViewActionsHandle.loadUrl`
     * lambda passed by the Composable closure). Returns `true` iff a non-empty
     * submit was made; the Composable uses this to decide whether to dismiss
     * focus + keyboard (FR-013a).
     *
     * Empty / whitespace-only input is a no-op (FR-012) and returns `false`.
     *
     * Spec 010 — the [AddressBarSubmitResult.Query] branch dispatches the
     * URL build through [buildSearchUrl] (a `BuildSearchUrlUseCase`) inside
     * [viewModelScope]. The function still returns `Boolean` synchronously so
     * the Composable can release focus + keyboard immediately per FR-013a; the
     * actual `loadUrl` invocation happens asynchronously inside the launched
     * coroutine after the use case resolves the engine + encodes the query.
     * The URL branch (no settings read needed) stays fully synchronous.
     */
    fun onAddressBarSubmit(loadUrl: (String) -> Unit): Boolean {
        val raw = _uiState.value.addressBarText
        return when (val classified = classifyAddressBarInput(raw)) {
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
    }

    /** Empty the address-bar text while preserving focus (FR-021). */
    fun onAddressBarClear() {
        _uiState.update { it.copy(addressBarText = "") }
    }

    /**
     * Spec 009 — single source of truth for [BrowserUiState.currentUrl] mutation.
     * Wired (in Phase 5 / US3) to fire from `onPageStarted` AND
     * `doUpdateVisitedHistory`, which gives the live URL trace through any
     * redirect chain (FR-019 / FR-019a / FR-019b). [onLoadStarted] no longer
     * touches `currentUrl` after the Spec 009 refactor — the responsibility
     * narrowed to loading-state transitions only.
     *
     * Spec 011 — additionally writes through to `TabRepository` so the active
     * tab's persisted URL stays current (FR-022).
     */
    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(currentUrl = url) }
        persistActiveTabState(url)
    }

    // ---------- Spec 011 — Tab management plumbing ----------

    /**
     * Spec 011 — write-through fires from `WebChromeClient.onReceivedTitle`
     * via `BrowserNavigationCallbacks.onTitleChange` (M1). Updates the
     * in-memory cache + persists immediately so process-death restoration
     * recovers the latest title (FR-022).
     */
    fun onTitleReceived(title: String) {
        currentTitleCache.value = title
        // Spec 014 FR-001 — the page announced its title after the load finished, which
        // is the common ordering. Patch it onto the row that was just written so the
        // history list shows the real title instead of the hostname fallback.
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

    /**
     * Spec 011 (Q2) — long-press on the 5th BottomAppBar button. Creates a
     * fresh home tab; on cap-reached, surfaces a one-shot
     * [TabsErrorEvent.MaxTabsReached] for the snackbar.
     */
    fun onLongPressNewTab() {
        viewModelScope.launch {
            val result = createTab(defaultHomeUrl)
            if (result is Result.Error && result.throwable is MaxTabsReachedException) {
                _uiState.update { it.copy(tabsEvent = TabsErrorEvent.MaxTabsReached) }
            }
        }
    }

    /** Clear the [BrowserUiState.tabsEvent] after the Composable shows the snackbar. */
    fun consumeTabsEvent() {
        _uiState.update { it.copy(tabsEvent = null) }
    }

    /**
     * Spec 011 favicon amendment (2026-05-03) — invoked by the WebView host
     * via `BrowserNavigationCallbacks.onIconReceived` on every
     * `WebChromeClient.onReceivedIcon`. Persists the favicon to the disk
     * cache keyed by SHA-1 of the URL hostname so subsequent tab card
     * lookups can find it synchronously at composition time.
     */
    fun onIconReceived(url: String, icon: Bitmap) {
        // Spec 012 FR-010 — incognito tabs MUST NOT write to the on-disk
        // favicon cache (the cache is hostname-keyed and would leak browsing
        // across the normal/incognito boundary).
        if (_uiState.value.isIncognito) return
        viewModelScope.launch {
            faviconCache.save(url, icon)
        }
    }

    /**
     * Spec 011 Q4 amendment (2026-05-03) — invoked ~200ms after
     * `onPageFinished` with the captured + downscaled WebView bitmap.
     * Persists via [ScreenshotCache] keyed by the **active tab id** (NOT
     * URL — see ScreenshotCache KDoc for the lifecycle rationale: bounded
     * by `MAX_TABS`, deterministic cleanup on tab close).
     *
     * If no active tab exists yet (rare; only between cold-start init and
     * the first Flow emission), the screenshot is dropped — the next
     * `onPageFinished` will fire another capture.
     */
    fun onScreenshotReady(bitmap: Bitmap) {
        // Spec 012 FR-009 — incognito tabs MUST NOT write screenshots to the
        // on-disk cache (privacy: file recovery from disk would leak page
        // content even after the tab is closed).
        if (_uiState.value.isIncognito) return
        val tabId = activeTabFlow.value?.id ?: return
        viewModelScope.launch {
            screenshotCache.save(tabId, bitmap)
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
        /** Sentinel for "no history row written" — see [lastRecordedHistoryId]. */
        const val NO_HISTORY_ROW: Long = 0L

        const val MAX_PROGRESS: Int = 100
        const val MAX_PROGRESS_F: Float = 100f
        const val SUBSCRIBE_TIMEOUT_MS: Long = 5_000L
        const val INITIAL_TAB_COUNT: Int = 1
    }
}
