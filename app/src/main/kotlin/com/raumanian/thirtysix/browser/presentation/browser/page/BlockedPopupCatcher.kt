package com.raumanian.thirtysix.browser.presentation.browser.page

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits

/**
 * Spec 021 research R5 — learns a blocked pop-up's address without loading it. A bare WebView
 * (JavaScript off, never shown) is handed to the page; its first navigation is reported and
 * stopped, then the WebView is destroyed. If the page names no address in time, `null` is
 * reported (the notice then has no Open).
 */
object BlockedPopupCatcher {
    private const val BLANK = "about:blank"

    fun attach(
        context: Context,
        transport: WebView.WebViewTransport,
        resultMsg: Message,
        onAddress: (String?) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val catcher = WebView(context)
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
