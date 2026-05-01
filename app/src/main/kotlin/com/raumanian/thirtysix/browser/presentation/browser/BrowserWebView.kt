@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Compose wrapper around [android.webkit.WebView] (Spec 007).
 *
 * Security posture (FR-006, FR-013, FR-017, FR-018; Constitution §I + §II):
 * - JavaScript enabled (FR-005), but ZERO `addJavascriptInterface` call sites
 *   anywhere in the codebase (FR-006, verified by grep CI gate).
 * - All four file-access flags disabled (FR-013).
 * - Mixed content `MIXED_CONTENT_NEVER_ALLOW` (FR-018).
 * - All web-origin runtime permission requests denied silently (FR-017).
 *
 * Lifecycle (FR-008, FR-014; research R2):
 * - `onPause` / `onResume` driven by host Activity lifecycle.
 * - `onDispose` calls `loadUrl("about:blank") + removeAllViews() + destroy()`
 *   to avoid the WebView 116+ native-resource race.
 *
 * Phasing — US3 (T031) ADDS:
 * - `onLoadFailed: (ErrorReason) -> Unit` parameter
 * - `WebViewClient` overrides: `onReceivedError`, `onReceivedHttpError`,
 *   `onReceivedSslError`
 */
@Composable
internal fun BrowserWebView(
    state: BrowserUiState,
    callbacks: BrowserWebViewCallbacks,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember {
        // Mutable holder; populated inside AndroidView.factory below.
        @Suppress("VariableNaming")
        arrayOfNulls<WebView>(1)
    }

    // Lifecycle: pause WebView when host stops, resume when started.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> webView[0]?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).also { wv ->
                webView[0] = wv
                applySecuritySettings(wv)
                wv.webViewClient = BrowserWebViewClient(
                    onLoadStarted = callbacks.onLoadStarted,
                    onLoadFinished = callbacks.onLoadFinished,
                    onLoadFailed = callbacks.onLoadFailed,
                )
                wv.webChromeClient = BrowserChromeClient(onProgressChanged = callbacks.onProgressChanged)
                wv.loadUrl(state.currentUrl)
            }
        },
    )

    // Native-resource cleanup. Keyed to Unit so this fires only once on
    // disposal (NOT every recomposition). See research.md R2.
    DisposableEffect(Unit) {
        onDispose {
            webView[0]?.let { wv ->
                wv.stopLoading()
                wv.loadUrl(BLANK_PAGE)
                wv.removeAllViews()
                wv.destroy()
            }
            webView[0] = null
        }
    }
}

/**
 * Centralized WebView security configuration. Every setting referenced here is
 * a Constitution gate: changing any of them MUST go through a spec amendment.
 *
 * NOTE for reviewers — cookies use the Android system default
 * (`CookieManager.acceptCookie() == true`). FR-016 mandates persistence; do
 * NOT add `setAcceptCookie(false)` or `removeAllCookies()` here without a
 * spec change. Spec 016 will introduce a user-driven "Clear data" UI.
 */
@Suppress("DEPRECATION")
// allowFileAccessFromFileURLs / allowUniversalAccessFromFileURLs are deprecated
// in newer WebView APIs but still mandated by Constitution §I as defense-in-depth
// for older WebView component versions on minSdk 24..API 33 devices. Setting
// them to `false` is harmless on devices where the flag has been removed (the
// system ignores deprecated setters). Constitution wins; suppression stays.
private fun applySecuritySettings(webView: WebView) {
    with(webView.settings) {
        @Suppress("SetJavaScriptEnabled") // FR-005 — JS required for modern web.
        javaScriptEnabled = true
        domStorageEnabled = true // localStorage / sessionStorage; local-only, no upload.

        // FR-013 — disable all four file-access vectors (Constitution §I).
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false

        // FR-018 — block all HTTP sub-resources on HTTPS pages.
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
    // FR-006 — addJavascriptInterface is FORBIDDEN; intentionally never called.
}

/**
 * Forwards page lifecycle + main-frame failures to [BrowserViewModel] state
 * transitions. Sub-frame errors are filtered out (do not surface to the user).
 *
 * Error mapping per research.md R6:
 * - `ERROR_HOST_LOOKUP` → [ErrorReason.DnsFailure]
 * - `ERROR_CONNECT | ERROR_TIMEOUT | ERROR_IO` → [ErrorReason.NetworkUnavailable]
 * - HTTP 4xx/5xx on main frame → [ErrorReason.HttpError]
 * - SSL handshake → [ErrorReason.SslError] (`handler.cancel()` ALWAYS — never `proceed()`)
 * - Anything else → [ErrorReason.Generic]
 */
private class BrowserWebViewClient(
    private val onLoadStarted: (String) -> Unit,
    private val onLoadFinished: (String) -> Unit,
    private val onLoadFailed: (ErrorReason) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        url?.let(onLoadStarted)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        url?.let(onLoadFinished)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame != true || error == null) return
        val reason = when (error.errorCode) {
            ERROR_HOST_LOOKUP -> ErrorReason.DnsFailure
            ERROR_CONNECT, ERROR_TIMEOUT, ERROR_IO -> ErrorReason.NetworkUnavailable
            else -> ErrorReason.Generic
        }
        onLoadFailed(reason)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame != true || errorResponse == null) return
        onLoadFailed(ErrorReason.HttpError(errorResponse.statusCode))
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        // Constitution §I — NEVER `proceed()`. Always cancel.
        handler?.cancel()
        onLoadFailed(ErrorReason.SslError)
    }
}

/**
 * Forwards progress and silently denies all web-origin runtime permissions
 * (FR-017). Manifest stays at three Constitution-mandated permissions.
 */
private class BrowserChromeClient(
    private val onProgressChanged: (Int) -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        // FR-017 — silently deny: allow=false, retain=false.
        callback?.invoke(origin, false, false)
    }
}

private const val BLANK_PAGE: String = "about:blank"
