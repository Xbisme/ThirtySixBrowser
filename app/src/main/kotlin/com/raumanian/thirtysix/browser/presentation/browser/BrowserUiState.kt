package com.raumanian.thirtysix.browser.presentation.browser

import com.raumanian.thirtysix.browser.presentation.tabs.TabsErrorEvent

/**
 * Immutable UI snapshot for the Browser screen.
 *
 * Constructed inside [BrowserViewModel] from the Hilt-injected
 * `@Named("default_home_url")` String — production binding lives in
 * [com.raumanian.thirtysix.browser.di.UrlConfigModule]; instrumented tests
 * construct the ViewModel directly with an explicit URL parameter (mirrors
 * `BrowserScreenOfflineErrorTest` pattern; see `BrowserScreenInstrumentedTest`).
 *
 * Spec 008 adds [canGoBack] / [canGoForward] derived from
 * `WebView.canGoBack() / canGoForward()` and recomputed inside
 * `WebViewClient.doUpdateVisitedHistory(...)` callbacks. Both default to
 * `false` so existing call sites that omit them continue to compile (binary
 * source compatibility for tests written before Spec 008).
 *
 * Spec 009 adds [addressBarText] / [isAddressBarFocused] for the top-bar
 * omnibox. Both transient — purely presentation state, never persisted, no
 * domain-model promotion. Defaults preserve binary compatibility.
 *
 * Spec 011 adds [tabsEvent] — a one-shot transient surface for the long-press
 * new-tab path on the BrowserScreen's 5th BottomAppBar button (Q2 / FR-002).
 * Currently only carries [TabsErrorEvent.MaxTabsReached] (FR-016 cap-reached
 * snackbar trigger); cleared by `BrowserViewModel.consumeTabsEvent()` after
 * the snackbar is shown.
 *
 * No `DEFAULT` companion exists by design: different injected URLs produce
 * different initial states, and a hard-coded const default would obscure that.
 */
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val addressBarText: String = "",
    val isAddressBarFocused: Boolean = false,
    val tabsEvent: TabsErrorEvent? = null,
    /**
     * Spec 012 — derived from the active tab's [com.raumanian.thirtysix.browser.domain.model.Tab.isIncognito]
     * via [com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase].
     *
     * Drives:
     *  - The address-bar incognito indicator (FR-015).
     *  - Cache-write gates in `BrowserViewModel.onIconReceived` /
     *    `onScreenshotReady` (FR-009 / FR-010).
     *  - WebView lockdown branch in [BrowserWebView] (per research.md R5).
     *
     * Default `false` preserves binary compatibility with all Spec 007/008/009/010/011
     * call sites and tests.
     */
    val isIncognito: Boolean = false,
    /**
     * Spec 013 — drives the star icon fill state on [BrowserScreen]'s top bar
     * (FR-002). Updates reactively via `IsUrlBookmarkedUseCase(currentUrl)`
     * collected in [BrowserViewModel.init].
     */
    val isBookmarked: Boolean = false,
    /**
     * Spec 015 — one-shot snackbar event for the download flow (FR-002 / FR-008a / FR-012).
     * Cleared via `BrowserViewModel.consumeDownloadSnackbarEvent()` once shown. Kept
     * separate from [bookmarkSnackbarEvent] so the two never overwrite each other.
     */
    val downloadSnackbarEvent: DownloadSnackbarEvent? = null,
    /**
     * Spec 013 — one-shot snackbar event after a star toggle (FR-001 / FR-003).
     * Cleared via `BrowserViewModel.consumeBookmarkSnackbarEvent()` after the
     * SnackbarHost has shown the message.
     */
    val bookmarkSnackbarEvent: BookmarkSnackbarEvent? = null,
)
