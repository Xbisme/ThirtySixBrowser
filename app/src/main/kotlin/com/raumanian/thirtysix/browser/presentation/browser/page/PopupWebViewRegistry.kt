package com.raumanian.thirtysix.browser.presentation.browser.page

import android.webkit.WebView
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec 021 data-model §6 / research R5 — WebViews a page created for a window the person asked
 * for, waiting to be adopted by their tab, and which tab opened each one (so `window.close()`
 * can return to it, FR-027). Main thread only.
 *
 * A pop-up is registered **before** its tab exists, under a unique placeholder address that the
 * tab is created with: the tab can be composed as soon as the tab store emits, which may be
 * before the creating coroutine resumes, so keying by tab id would race.
 */
@Singleton
class PopupWebViewRegistry @Inject constructor() {
    private val sequence = AtomicLong()
    private val waiting = mutableMapOf<String, WebView>()

    // Weak keys: a released WebView must not be kept alive by this bookkeeping.
    private val openers = WeakHashMap<WebView, Long>()
    private val tabs = WeakHashMap<WebView, Long>()
    private val closedEarly = WeakHashMap<WebView, Boolean>()

    /** Holds [webView] and returns the placeholder address its tab must be created with. */
    fun hold(webView: WebView, openerTabId: Long): String {
        val address = UrlConstants.POPUP_PLACEHOLDER_URL + PLACEHOLDER_FRAGMENT + sequence.incrementAndGet()
        waiting[address] = webView
        openers[webView] = openerTabId
        return address
    }

    /** The tab created for [webView] exists now. */
    fun onTabCreated(webView: WebView, tabId: Long) {
        tabs[webView] = tabId
    }

    /** The WebView waiting for a tab whose address is [tabUrl], handed over once. */
    fun take(tabUrl: String): WebView? = waiting.remove(tabUrl)

    /** The page-opened tab [webView] belongs to and the tab that opened it, or `null`. */
    fun popupOf(webView: WebView): Pair<Long, Long>? {
        val tab = tabs[webView]
        val opener = openers[webView]
        return if (tab == null || opener == null) null else tab to opener
    }

    /** The page closed [webView] before its tab existed; the tab is closed once created. */
    fun closeWhenCreated(webView: WebView) {
        closedEarly[webView] = true
    }

    fun wasClosedEarly(webView: WebView): Boolean = closedEarly.remove(webView) == true

    fun forget(webView: WebView) {
        closedEarly.remove(webView)
        waiting.values.remove(webView)
        tabs.remove(webView)
        openers.remove(webView)
    }

    private companion object {
        const val PLACEHOLDER_FRAGMENT = "#thirtysix-popup-"
    }
}
