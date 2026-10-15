package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
/**
 * The protections as one WebView sees them. `BrowserWebView` routes its loads and client callbacks
 * through here; without an instance (bare test WebViews) nothing is protected.
 */
class WebViewProtection(
    private val engine: ProtectionEngine,
    val monitor: PageProtectionMonitor,
) {
    @Volatile
    private var released = false

    /** Every app-issued load: parameters stripped, `http://` upgraded, GPC added if not app-wide. */
    fun load(webView: WebView, url: String) {
        engine.prepare(url, monitor.isIncognito) { prepared ->
            if (released) return@prepare
            monitor.beginUpgrade(prepared)
            webView.loadUrl(prepared.url, GpcHeaderInstaller.headersForLoad())
        }
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) {
            monitor.onMainFrameRequest(request.url.toString())
            return null
        }
        return monitor.onSubresource(request.url)
    }

    /** For a page-started main-frame http(s) navigation: `true` when replaced by its prepared form. */
    fun overrideNavigation(webView: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase()
        val isWebMainFrame = request.isForMainFrame &&
            (scheme == UrlConstants.SCHEME_HTTP || scheme == UrlConstants.SCHEME_HTTPS)
        return when {
            !isWebMainFrame -> false
            // An HTTPS attempt sent back to http:// is remembered; other redirects are never rewritten.
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

    /** A failed pending HTTPS attempt returns `true`, so no error is shown. */
    fun fallBackIfUpgrade(failedUrl: String?): Boolean = monitor.onMainFrameFailure(failedUrl) != null

    /**
     * Loads the original `http://` address once a failed HTTPS attempt's error page has finished;
     * returns `true` then, so the error page is not reported as a visit.
     */
    fun onPageFinished(webView: WebView, url: String?): Boolean {
        monitor.onPageFinished(url)
        val http = monitor.takeFallbackDue() ?: return false
        if (!released) webView.loadUrl(http, GpcHeaderInstaller.headersForLoad())
        return true
    }

    fun release() {
        released = true
    }
}
