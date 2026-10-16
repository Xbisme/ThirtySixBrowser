package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.os.Bundle
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits

/** Restores and saves one tab's back and forward through its WebView. */
internal class TabSessionHooks(
    private val tabId: Long,
    private val savedState: SavedTabStateLookup,
    private val onAction: (TabSessionAction) -> Unit,
) {
    private var pending: Runnable? = null

    /** True when the WebView now holds the tab's saved pages; it then reloads the current one itself. */
    fun restore(webView: WebView): Boolean {
        val bundle = savedState.savedState(tabId)?.let(TabStateCodec::decode) ?: return false
        return runCatching { webView.restoreState(bundle) != null && webView.copyBackForwardList().size > 0 }
            .getOrDefault(false)
    }

    /** After a load or a history change: single-page sites change history often, so saves are batched. */
    fun saveSoon(webView: WebView) {
        cancelPending(webView)
        val save = Runnable { saveNow(webView) }
        pending = save
        webView.postDelayed(save, BrowserLimits.TAB_STATE_SAVE_DEBOUNCE_MS)
    }

    /** Before the WebView goes, or when the app leaves the screen. */
    fun saveNow(webView: WebView) {
        cancelPending(webView)
        val url = webView.url
        if (url.isNullOrEmpty() || url == ABOUT_BLANK || webView.copyBackForwardList().size == 0) return
        val bundle = Bundle()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
            WebViewCompat.saveState(webView, bundle, BrowserLimits.TAB_STATE_MAX_BYTES, true)
        } else {
            webView.saveState(bundle)
        }
        val state = TabStateCodec.encode(bundle) ?: return
        onAction(TabSessionAction.Save(tabId, state))
    }

    private fun cancelPending(webView: WebView) {
        pending?.let(webView::removeCallbacks)
        pending = null
    }

    private companion object {
        // A released WebView loads this before it is destroyed; that is never the tab's page.
        const val ABOUT_BLANK = "about:blank"
    }
}
