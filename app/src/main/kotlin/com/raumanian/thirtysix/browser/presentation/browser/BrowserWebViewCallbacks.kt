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
 *
 * Spec 015 adds [onDownloadRequested] (5 fields). Still under detekt's
 * `LongParameterList.constructorThreshold = 7`, and the rule ignores data classes anyway.
 */
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
    /**
     * Spec 015 FR-001 — the web engine declined to render a resource and handed it back
     * as a download. Fired from `WebView.setDownloadListener`; the content length the
     * platform also supplies is dropped, since the size shown in the list is read from the
     * platform at display time rather than captured here (FR-015).
     */
    val onDownloadRequested: (
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) -> Unit,
)
