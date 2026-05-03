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
import androidx.core.graphics.scale
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
@Suppress("LongParameterList")
// 6 params after Spec 009: state + homeUrl + actions + 2 callback bundles + modifier.
// Spec 008's split of [BrowserWebViewCallbacks] (6 fields → 4) and the new
// [BrowserNavigationCallbacks] (3 fields) was specifically structured to keep
// each bundle's data-class constructor under detekt's `constructorThreshold = 7`.
// Bundling the two callback objects further into a wrapper would defeat that
// split (pushing the wrapper to 7 fields again) without semantic gain. The
// 6th param here is intentional and tied to the spec design.
@Composable
internal fun BrowserWebView(
    state: BrowserUiState,
    homeUrl: String,
    actions: WebViewActionsHandle,
    callbacks: BrowserWebViewCallbacks,
    navigationCallbacks: BrowserNavigationCallbacks,
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

    // Spec 012 — capture the incognito flag at first composition. The flag
    // never changes within a single BrowserWebView instance lifetime (a tab
    // either is or isn't incognito for its entire life), so capturing-at-first
    // is correct and safe.
    val isIncognito = state.isIncognito

    AndroidView(
        modifier = modifier,
        factory = { context ->
            buildConfiguredWebView(
                context = context,
                state = state,
                homeUrl = homeUrl,
                actions = actions,
                callbacks = callbacks,
                navigationCallbacks = navigationCallbacks,
            ).also { wv -> webView[0] = wv }
        },
    )

    // Native-resource cleanup. Keyed to Unit so this fires only once on
    // disposal (NOT every recomposition). See research.md R2.
    //
    // Spec 012 R5 — incognito tabs run a 9-step teardown that prepends 5
    // clear*() calls before the existing Spec 007 4-step destroy, ensuring
    // no per-WebView state (history, form data, find-on-page, SSL decisions,
    // disk cache) outlives the close-tab moment. Order is fixed; each clear*
    // is idempotent so re-entry under stress is harmless.
    DisposableEffect(Unit) {
        onDispose {
            webView[0]?.let { wv ->
                wv.stopLoading()
                if (isIncognito) {
                    wv.clearHistory()
                    wv.clearFormData()
                    wv.clearMatches()
                    wv.clearSslPreferences()
                    // includeDiskFiles = true → wipe disk cache too.
                    wv.clearCache(true)
                }
                wv.loadUrl(BLANK_PAGE)
                wv.removeAllViews()
                wv.destroy()
            }
            webView[0] = null
        }
    }
}

/**
 * Spec 011 — extracted from [BrowserWebView] factory body to keep the host
 * Composable under detekt's `LongMethod = 60` threshold after the favicon
 * amendment (2026-05-03) added `onIconReceived` plumbing.
 */
@Suppress("LongParameterList")
private fun buildConfiguredWebView(
    context: android.content.Context,
    state: BrowserUiState,
    homeUrl: String,
    actions: WebViewActionsHandle,
    callbacks: BrowserWebViewCallbacks,
    navigationCallbacks: BrowserNavigationCallbacks,
): WebView = WebView(context).also { wv ->
    applySecuritySettings(wv)
    if (state.isIncognito) applyIncognitoSettings(wv)
    wv.webViewClient = BrowserWebViewClient(
        onLoadStarted = callbacks.onLoadStarted,
        onLoadFinished = callbacks.onLoadFinished,
        onLoadFailed = callbacks.onLoadFailed,
        onUrlChange = navigationCallbacks.onUrlChange,
        onCanGoBackChange = navigationCallbacks.onCanGoBackChange,
        onCanGoForwardChange = navigationCallbacks.onCanGoForwardChange,
        onScreenshotReady = navigationCallbacks.onScreenshotReady,
    )
    wv.webChromeClient = BrowserChromeClient(
        onProgressChanged = callbacks.onProgressChanged,
        onReceivedTitle = navigationCallbacks.onTitleChange,
        onReceivedIcon = navigationCallbacks.onIconReceived,
    )
    // Spec 008 — wire imperative handle. Each lambda captures `wv` via the
    // factory closure; the lambdas persist for the lifetime of the WebView.
    actions.goBack = { if (wv.canGoBack()) wv.goBack() }
    actions.goForward = { if (wv.canGoForward()) wv.goForward() }
    actions.reload = { wv.reload() }
    actions.stopLoading = { wv.stopLoading() }
    actions.loadHome = { wv.loadUrl(homeUrl) }
    actions.loadUrl = { url -> wv.loadUrl(url) }

    // Spec 008 — conditional initial load. When state is seeded to Failed
    // (instrumented test pattern), skip the initial loadUrl so the test
    // assertion on the error UI is not overridden by a successful auto-load.
    if (state.loadingState !is LoadingState.Failed) {
        wv.loadUrl(state.currentUrl)
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
 * Spec 012 — additional WebView lockdown applied ONLY to incognito tabs
 * (research.md R5). Sits on top of [applySecuritySettings], inheriting all
 * Spec 007 protections; adds:
 *
 *  - `saveFormData = false` (FR-008): no autofill capture from form fields.
 *    Deprecated since API 26 but still honoured on minSdk 24..API 25 devices;
 *    setting it costs nothing on newer Androids.
 *  - `cacheMode = LOAD_NO_CACHE` (FR-011): every request bypasses disk cache,
 *    so no page resource artefact is left behind for forensic recovery.
 *
 * The on-destroy `clear*()` sequence is in [BrowserWebView]'s
 * [DisposableEffect] cleanup (R5 9-step teardown).
 */
@Suppress("DEPRECATION")
// setSaveFormData is deprecated since API 26 but Constitution §I + FR-008
// require explicit disable on older devices. Suppression scoped to this
// single setter; setting on newer Androids is a no-op.
private fun applyIncognitoSettings(webView: WebView) {
    with(webView.settings) {
        saveFormData = false
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
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
@Suppress("LongParameterList")
private class BrowserWebViewClient(
    private val onLoadStarted: (String) -> Unit,
    private val onLoadFinished: (String) -> Unit,
    private val onLoadFailed: (ErrorReason) -> Unit,
    private val onUrlChange: (String) -> Unit,
    private val onCanGoBackChange: (Boolean) -> Unit,
    private val onCanGoForwardChange: (Boolean) -> Unit,
    private val onScreenshotReady: (Bitmap) -> Unit,
) : WebViewClient() {
    /**
     * Spec 012 (T065 / FR-018) — external-intent crash safety.
     *
     * Non-http(s) URLs (`tel:`, `mailto:`, `intent://`, custom app schemes)
     * are dispatched to [Intent.parseUri] + `startActivity`. Both calls can
     * throw on malformed URIs ([URISyntaxException]) or on devices without
     * a handler ([ActivityNotFoundException], [SecurityException]). A single
     * outer `try { … } catch (Throwable) { … }` consumes the URL silently
     * (`return true`) and prevents any platform exception from propagating
     * into the Compose tree — strengthens both Spec 007/008 baseline AND
     * the FR-018 incognito requirement (the wrapper applies universally).
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    // Intentional generic catch + 3 early returns — defence-in-depth against
    // any platform call that may throw on a misbehaving handler app or
    // malformed URI. Spec 012 FR-018 mandates "MUST NOT crash"; broad catch
    // is the simplest correct fix.
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in WEB_NATIVE_SCHEMES) return false // let WebView load it normally
        val context = view?.context ?: return false
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            android.util.Log.w("BrowserWebView", "External intent dispatch failed: $uri", t)
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        url?.let {
            // Spec 009 — fire URL change FIRST so the address bar reflects
            // the submitted (or redirected-to) URL on the same UI tick the
            // load starts (FR-019a / FR-019b live mirror).
            onUrlChange(it)
            onLoadStarted(it)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        url?.let(onLoadFinished)
        // Spec 011 Q4 amendment — schedule a screenshot capture after the
        // WebView has actually painted. `postDelayed` defers past the next
        // layout pass + buffers for animated / progressive content (e.g.,
        // Google logo fade-in) so the captured bitmap shows the
        // fully-rendered page. The cache is tab-id-keyed at the consumer
        // side; we just hand off the raw bitmap here.
        val v = view ?: return
        v.postDelayed({ captureScreenshot(v) }, SCREENSHOT_DELAY_MS)
    }

    private fun captureScreenshot(view: WebView) {
        if (view.width <= 0 || view.height <= 0) return
        try {
            // Spec 011 Q4 amendment (2026-05-03 user feedback) — crop the
            // top portion of the WebView to 16:9 BEFORE scaling so the
            // preview keeps the original page's aspect ratio (no horizontal
            // stretch / vertical squish that `createScaledBitmap(480, 270)`
            // alone would produce on a portrait WebView surface).
            //
            // Algorithm:
            //  1. Compute a 16:9 crop region anchored at the top of the
            //     WebView, height = width × (270 / 480). If the WebView is
            //     itself shorter than that (rare — narrow phone landscape),
            //     fall back to the full height.
            //  2. Allocate a bitmap of the crop dimensions and draw the
            //     WebView into it. WebView renders top-left aligned, so the
            //     header/visible-viewport portion of the page is captured.
            //  3. Scale the crop down to 480×270 with bilinear filtering
            //     (preserves aspect; no distortion).
            val targetW = com.raumanian.thirtysix.browser.core.constants.AppConstants.SCREENSHOT_TARGET_WIDTH_PX
            val targetH = com.raumanian.thirtysix.browser.core.constants.AppConstants.SCREENSHOT_TARGET_HEIGHT_PX
            val cropHeight = ((view.width.toLong() * targetH) / targetW)
                .toInt()
                .coerceAtMost(view.height)
                .coerceAtLeast(1)
            val source = androidx.core.graphics.createBitmap(
                view.width,
                cropHeight,
                Bitmap.Config.ARGB_8888,
            )
            view.draw(android.graphics.Canvas(source))
            // filter = true → bilinear scaling for smoother downscale.
            val scaled = source.scale(targetW, targetH, filter = true)
            if (scaled !== source) source.recycle()
            onScreenshotReady(scaled)
        } catch (e: OutOfMemoryError) {
            // Bitmap allocation failed (large WebView surface, low memory).
            // Fall back to placeholder; not a crash.
            android.util.Log.w("BrowserWebView", "Screenshot OOM", e)
        }
    }

    /**
     * Spec 008 — fires AFTER each successful page commit (incl. fragment nav,
     * History API push, back/forward). Re-reads platform truth and pushes to
     * the ViewModel. Lighter than `copyBackForwardList()` (which allocates a
     * full snapshot per call); we only need three signals.
     *
     * Spec 009 — also fires [onUrlChange] so SPA-style `pushState` /
     * `replaceState` URL transitions flow into the live mirror (FR-019).
     */
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val v = view ?: return
        url?.let(onUrlChange)
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
 * Forwards progress, page title (Spec 011 / M1), favicon (Spec 011 favicon
 * amendment 2026-05-03), and silently denies all web-origin runtime
 * permissions (FR-017). Manifest stays at three Constitution-mandated
 * permissions.
 */
private class BrowserChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onReceivedTitle: (String) -> Unit,
    private val onReceivedIcon: (String, android.graphics.Bitmap) -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        title?.let(onReceivedTitle)
    }

    override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
        val url = view?.url ?: return
        if (icon != null) onReceivedIcon(url, icon)
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

/**
 * Spec 012 FR-018 — schemes the WebView itself loads natively. Anything else
 * (`tel:`, `mailto:`, `intent://`, custom app schemes) is dispatched as an
 * external Intent inside [shouldOverrideUrlLoading] with full crash safety.
 */
private val WEB_NATIVE_SCHEMES: Set<String> = setOf("http", "https", "about", "data")

/**
 * Spec 011 Q4 amendment — delay between `onPageFinished` and the screenshot
 * capture. 200 ms gives the WebView time to finish painting (animated
 * elements like the Google logo fade-in, progressive image loads) before
 * we grab the bitmap. Lower → risk of capturing a half-painted frame; higher
 * → more wasted memory if the user navigates away quickly.
 */
private const val SCREENSHOT_DELAY_MS: Long = 200L
