package com.raumanian.thirtysix.browser.presentation.browser.protection

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.raumanian.thirtysix.browser.core.constants.UrlConstants

/**
 * Spec 023 — the protections as one WebView sees them. `BrowserWebView` routes its loads and its
 * `WebViewClient` callbacks through here; with no instance (tests that build a bare WebView)
 * everything behaves as before Spec 023.
 */
class WebViewProtection(
    private val engine: ProtectionEngine,
    val monitor: PageProtectionMonitor,
) {
    @Volatile
    private var released = false

    /**
     * Every address the app loads goes through here (research R7): tracking parameters removed,
     * `http://` upgraded, the GPC header added where it is not app-wide. Re-issued navigations
     * carry no referrer (FR-028).
     */
    fun load(webView: WebView, url: String) {
        engine.prepare(url, monitor.isIncognito) { prepared ->
            if (released) return@prepare
            monitor.beginUpgrade(prepared)
            webView.loadUrl(prepared.url, GpcHeaderInstaller.headersForLoad())
        }
    }

    /** `shouldInterceptRequest` (IO thread). */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) {
            monitor.onMainFrameRequest(request.url.toString())
            return null
        }
        return monitor.onSubresource(request.url)
    }

    /**
     * `shouldOverrideUrlLoading` for a main-frame http(s) navigation started by the page.
     * Returns `true` when the navigation was replaced by its prepared form.
     */
    fun overrideNavigation(webView: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase()
        val isWebMainFrame = request.isForMainFrame &&
            (scheme == UrlConstants.SCHEME_HTTP || scheme == UrlConstants.SCHEME_HTTPS)
        return when {
            !isWebMainFrame -> false
            // FR-019 — an HTTPS attempt sent back to http:// is allowed through and remembered;
            // other redirects are never rewritten (FR-027).
            request.isRedirect -> {
                monitor.onRedirect(url)
                false
            }
            else -> reissueIfChanged(webView, url)
        }
    }

    /** This callback must answer now; if the lists are still loading the link loads as is. */
    private fun reissueIfChanged(webView: WebView, url: String): Boolean {
        val prepared = engine.prepareNow(url, monitor.isIncognito)?.takeIf { it.url != url } ?: return false
        monitor.beginUpgrade(prepared)
        webView.loadUrl(prepared.url, GpcHeaderInstaller.headersForLoad())
        return true
    }

    /**
     * A main-frame load failed. When it was a pending HTTPS attempt, the original `http://`
     * address is loaded instead and `true` is returned: no error is shown (FR-019).
     */
    fun fallBackIfUpgrade(webView: WebView, failedUrl: String?): Boolean {
        val http = monitor.onMainFrameFailure(failedUrl) ?: return false
        webView.loadUrl(http, GpcHeaderInstaller.headersForLoad())
        return true
    }

    fun release() {
        released = true
    }
}
