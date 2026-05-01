package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Bundles the event callbacks `BrowserWebView` forwards to the ViewModel.
 * Keeps the Composable parameter count under detekt's `LongParameterList`
 * threshold (6) without sacrificing readability.
 *
 * Spec 007 baseline: 4 callbacks (load lifecycle + progress + error).
 * Spec 008 adds 2 history-state callbacks (total 6, exactly at threshold) so
 * the ViewModel can reflect WebView session-history changes into
 * [BrowserUiState.canGoBack] / [BrowserUiState.canGoForward] reactively.
 */
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,
    val onCanGoForwardChange: (Boolean) -> Unit,
)
