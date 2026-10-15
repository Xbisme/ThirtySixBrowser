package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
/**
 * Learns a blocked pop-up's address without loading it: a bare, never-shown WebView with JavaScript
 * off is handed to the page, and its first navigation is reported and stopped. No address in time
 * reports `null`, so the notice has no Open.
 */
object BlockedPopupCatcher {
    private const val BLANK = "about:blank"

    // Lint's `MissingOnRenderProcessGone` also flags the superclass constructor call; the override exists.
    @android.annotation.SuppressLint("MissingOnRenderProcessGone")
    fun attach(
        catcher: WebView,
        transport: WebView.WebViewTransport,
        resultMsg: Message,
        onAddress: (String?) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        var reported = false
        val report: (String?) -> Unit = { address ->
            if (!reported) {
                reported = true
                handler.removeCallbacksAndMessages(null)
                onAddress(address)
                handler.post {
                    catcher.stopLoading()
                    catcher.destroy()
                }
            }
        }
        catcher.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                report(request?.url?.toString())
                return true
            }

            // Losing this WebView's renderer must not take the app down: forget the address.
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                report(null)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                // A new window starts on about:blank before the page navigates it.
                if (url != BLANK) report(url)
            }
        }
        transport.webView = catcher
        resultMsg.sendToTarget()
        handler.postDelayed({ report(null) }, BrowserLimits.BLOCKED_POPUP_ADDRESS_WAIT_MS)
    }
}
