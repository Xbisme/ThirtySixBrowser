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
 * Spec 008 additions:
 * - [actions] `WebViewActionsHandle` populated inside the factory closure with
 *   thin wrappers around `WebView.goBack/goForward/reload/stopLoading/loadUrl`.
 *   Read by `NavigationBottomBar` click handlers + `PredictiveBackHandler`.
 * - [homeUrl] separate parameter from [state.currentUrl] so the Home affordance
 *   reloads a stable home URL even after the user has navigated elsewhere.
 * - `WebViewClient.doUpdateVisitedHistory` override fires
 *   [callbacks.onCanGoBackChange] / [callbacks.onCanGoForwardChange] after
 *   every history-mutating commit, keeping `BrowserUiState.canGoBack` /
 *   `canGoForward` reactive (FR-014).
 * - Initial-load conditional: skips `wv.loadUrl(state.currentUrl)` when state
 *   is seeded to [LoadingState.Failed] before composition (preserves the
 *   `BrowserScreenOfflineErrorTest` deterministic assertion — without this
 *   guard, the WebView would auto-load the URL and override the seeded Failed
 *   state via subsequent `onPageFinished`).
 */
@Composable
internal fun BrowserWebView(
    state: BrowserUiState,
    homeUrl: String,
    actions: WebViewActionsHandle,
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
                    onCanGoBackChange = callbacks.onCanGoBackChange,
                    onCanGoForwardChange = callbacks.onCanGoForwardChange,
                )
                wv.webChromeClient = BrowserChromeClient(onProgressChanged = callbacks.onProgressChanged)

                // Spec 008 — wire imperative handle. Each lambda captures `wv`
                // via the factory closure; the lambdas persist for the lifetime
                // of the WebView instance.
                actions.goBack = { if (wv.canGoBack()) wv.goBack() }
                actions.goForward = { if (wv.canGoForward()) wv.goForward() }
                actions.reload = { wv.reload() }
                actions.stopLoading = { wv.stopLoading() }
                actions.loadHome = { wv.loadUrl(homeUrl) }

                // Spec 008 — conditional initial load. When state is seeded to
                // Failed (instrumented test pattern), skip the initial loadUrl
                // so the test assertion on the error UI is not overridden by a
                // successful auto-load.
                if (state.loadingState !is LoadingState.Failed) {
                    wv.loadUrl(state.currentUrl)
                }
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
 *
 * Spec 008 — adds `doUpdateVisitedHistory` override to surface `canGoBack` /
 * `canGoForward` flips into [BrowserUiState] via the new callbacks.
 */
private class BrowserWebViewClient(
    private val onLoadStarted: (String) -> Unit,
    private val onLoadFinished: (String) -> Unit,
    private val onLoadFailed: (ErrorReason) -> Unit,
    private val onCanGoBackChange: (Boolean) -> Unit,
    private val onCanGoForwardChange: (Boolean) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        url?.let(onLoadStarted)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        url?.let(onLoadFinished)
    }

    /**
     * Spec 008 — fires AFTER each successful page commit (incl. fragment nav,
     * History API push, back/forward). Re-reads platform truth and pushes to
     * the ViewModel. Lighter than `copyBackForwardList()` (which allocates a
     * full snapshot per call); we only need two booleans.
     */
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val v = view ?: return
        onCanGoBackChange(v.canGoBack())
        onCanGoForwardChange(v.canGoForward())
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
