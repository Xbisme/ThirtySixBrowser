package com.raumanian.thirtysix.browser.presentation.browser

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
)
