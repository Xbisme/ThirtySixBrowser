package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Bundles the load-lifecycle callbacks `BrowserWebView` forwards to the
 * ViewModel.
 *
 * Spec 007 baseline: 4 callbacks (page lifecycle + progress + error).
 * Spec 008 added 2 history-state callbacks (total 6 = at threshold).
 * Spec 009 split: the 2 history-state callbacks moved into
 * [BrowserNavigationCallbacks] alongside the new `onUrlChange` callback,
 * leaving this bundle at its original Spec 007 size of 4 fields. Mirrors
 * Spec 008's `NavigationBottomBarCallbacks` extraction precedent.
 */
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
)
