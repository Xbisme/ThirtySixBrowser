package com.raumanian.thirtysix.browser.presentation.browser.page

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Spec 021 research R5 — the client of a page-opened WebView until its tab adopts it. Its only
 * jobs: navigations load in place (a WebView with no client hands them to another app), and a
 * lost renderer destroys this WebView instead of ending the app.
 */
// androidx.webkit's `MissingOnRenderProcessGone` also reports the `WebViewClient` constructor
// call, which in Kotlin includes this subclass's own superclass call; the override exists
// (Spec 016 finding, see BrowserWebViewClient).
@android.annotation.SuppressLint("MissingOnRenderProcessGone")
class PendingPopupWebViewClient : WebViewClient() {
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.destroy()
        return true
    }
}
