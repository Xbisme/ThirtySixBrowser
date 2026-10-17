package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageRequestSink
/** The load-lifecycle callbacks `BrowserWebView` forwards to the view model. */
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
    // The content length is dropped: the downloads list reads the size from the platform when shown.
    val onDownloadRequested: (
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) -> Unit,
    /** Inert by default, so tests that build the WebView directly need nothing. */
    val pageRequests: PageRequestSink = PageRequestSink.None,
    /** 0-based active match and the number of matches. */
    val onFindResult: (activeOrdinal: Int, total: Int) -> Unit = { _, _ -> },
    /** The page has drawn its first frame since a navigation began. */
    val onPageVisible: () -> Unit = {},
)
