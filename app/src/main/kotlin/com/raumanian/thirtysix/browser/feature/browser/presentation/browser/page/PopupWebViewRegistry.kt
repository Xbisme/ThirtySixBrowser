package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.webkit.WebView
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Page-created WebViews waiting for their tab, and which tab opened each. Registered before the
 * tab exists, under a unique placeholder address, because the tab can compose before the creating
 * coroutine resumes. Main thread only.
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

    fun onTabCreated(webView: WebView, tabId: Long) {
        tabs[webView] = tabId
    }

    fun take(tabUrl: String): WebView? = waiting.remove(tabUrl)

    /** The page-opened tab [webView] belongs to and the tab that opened it, or `null`. */
    fun popupOf(webView: WebView): Pair<Long, Long>? {
        val tab = tabs[webView]
        val opener = openers[webView]
        return if (tab == null || opener == null) null else tab to opener
    }

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
