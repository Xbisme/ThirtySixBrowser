package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Bundles the four event callbacks `BrowserWebView` forwards to the ViewModel.
 * Keeps the Composable parameter count under detekt's `LongParameterList`
 * threshold without sacrificing readability.
 */
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
)
