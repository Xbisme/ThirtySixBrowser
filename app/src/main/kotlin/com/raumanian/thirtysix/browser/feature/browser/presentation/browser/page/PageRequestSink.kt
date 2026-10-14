package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebView
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.PopupDecision
/** Spec 021 — which page a request belongs to (data-model §3, §4). */
data class PageContext(val tabId: Long, val generation: Long, val isIncognito: Boolean)

/**
 * Spec 021 research R1 — how the WebView clients hand page requests to the browser screen. The
 * clients translate platform callbacks; everything that involves the person happens behind this
 * interface. [None] is the inert default for previews and tests that build the WebView directly.
 */
@Suppress("TooManyFunctions") // One entry point per kind of page request (C3.1).
interface PageRequestSink {
    /** The page currently shown, or `null` when there is none. */
    fun context(): PageContext?

    /** A new main-frame navigation started: the previous page's requests are stale (FR-034). */
    fun onNavigationStarted()

    /** Queues [request] for the person. */
    fun enqueue(request: PageRequest)

    /**
     * Spec 021 research R2 — a camera / microphone / location request: decided silently when a
     * decision exists, otherwise queued for the person. [hasUnsupported] → refused (FR-008).
     */
    fun onPermissionRequest(request: PageRequest.Permission, hasUnsupported: Boolean)

    /** The page withdrew permission requests matching [match] before they were answered. */
    fun onPermissionWithdrawn(match: (PageRequest.Permission) -> Boolean) = Unit

    /** Spec 021 research R4 — the page's fullscreen view; inert sinks refuse it. */
    fun onShowCustomView(view: View, callback: CustomViewCallback) = callback.onCustomViewHidden()

    /** The page left fullscreen itself. */
    fun onHideCustomView() = Unit

    /**
     * Spec 021 research R5 — the page asked for a new window. `null` refuses it (no page, or no
     * pop-up support where the WebView is built directly).
     */
    fun decideWindow(isUserGesture: Boolean): PopupDecision? = null

    /** The page-created WebView for an allowed window; it becomes a new tab. */
    fun adoptPopup(popup: WebView) = popup.destroy()

    /**
     * A blocked window's address, or `null` when the page named none. [generation] is the page
     * that asked: a notice for a page that has since been replaced is dropped.
     */
    fun onPopupBlocked(target: String?, generation: Long) = Unit

    /** `window.close()` from the page in [window]. */
    fun onCloseWindow(window: WebView) = Unit

    /** Spec 021 C1.5 — whether the current page may show a dialog; `null` when there is no page. */
    fun admitDialog(): DialogAdmission? = null

    object None : PageRequestSink {
        override fun context(): PageContext? = null

        override fun onNavigationStarted() = Unit

        override fun enqueue(request: PageRequest) {
            request.cancel()
        }

        override fun onPermissionRequest(request: PageRequest.Permission, hasUnsupported: Boolean) {
            request.deny()
        }
    }
}
