package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The client of a page-opened WebView until its tab adopts it: navigations load in place (with no
 * client they go to another app), and a lost renderer destroys this WebView, not the app.
 */
// Lint's `MissingOnRenderProcessGone` also flags the superclass constructor call; the override exists.
@android.annotation.SuppressLint("MissingOnRenderProcessGone")
class PendingPopupWebViewClient : WebViewClient() {
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.destroy()
        return true
    }
}
