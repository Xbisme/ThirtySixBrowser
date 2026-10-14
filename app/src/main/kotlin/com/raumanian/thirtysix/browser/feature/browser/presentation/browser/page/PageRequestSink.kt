package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebView
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.PopupDecision
data class PageContext(val tabId: Long, val generation: Long, val isIncognito: Boolean)

/**
 * How the WebView clients hand page requests to the browser screen; everything involving the
 * person happens behind it. [None] is the inert default for previews and direct-WebView tests.
 */
@Suppress("TooManyFunctions") // One entry point per kind of page request.
interface PageRequestSink {
    fun context(): PageContext?

    fun onNavigationStarted()

    fun enqueue(request: PageRequest)

    /** Decided silently when a decision exists, otherwise queued; [hasUnsupported] is refused. */
    fun onPermissionRequest(request: PageRequest.Permission, hasUnsupported: Boolean)

    fun onPermissionWithdrawn(match: (PageRequest.Permission) -> Boolean) = Unit

    fun onShowCustomView(view: View, callback: CustomViewCallback) = callback.onCustomViewHidden()

    fun onHideCustomView() = Unit

    fun decideWindow(isUserGesture: Boolean): PopupDecision? = null

    fun adoptPopup(popup: WebView) = popup.destroy()

    /** A blocked window's address, or `null`; a notice for a page since replaced is dropped. */
    fun onPopupBlocked(target: String?, generation: Long) = Unit

    fun onCloseWindow(window: WebView) = Unit

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
