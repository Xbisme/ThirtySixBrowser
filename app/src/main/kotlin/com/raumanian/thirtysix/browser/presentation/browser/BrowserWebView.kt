@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.scale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.domain.model.LongPressHit
import com.raumanian.thirtysix.browser.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.domain.usecase.BuildLongPressMenuUseCase
import com.raumanian.thirtysix.browser.presentation.browser.page.PageRequest
import com.raumanian.thirtysix.browser.presentation.browser.page.PageRequestSink
import com.raumanian.thirtysix.browser.presentation.browser.page.PendingPopupWebViewClient

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
 * - Spec 019 — the `homeUrl` parameter and `loadHome` action were removed: Home is now a
 *   page drawn by the app over the WebView (research R1), never an address loaded into it.
 * - `WebViewClient.doUpdateVisitedHistory` override fires
 *   [callbacks.onCanGoBackChange] / [callbacks.onCanGoForwardChange] after
 *   every history-mutating commit, keeping `BrowserUiState.canGoBack` /
 *   `canGoForward` reactive (FR-014).
 * - Initial-load conditional: skips `wv.loadUrl(state.currentUrl)` when state
 *   is seeded to [LoadingState.Failed] before composition (preserves the
 *   `BrowserScreenOfflineErrorTest` deterministic assertion — without this
 *   guard, the WebView would auto-load the URL and override the seeded Failed
 *   state via subsequent `onPageFinished`).
 *
 * Renderer recovery (Spec 016 carry-forward debt, lint `MissingOnRenderProcessGone`):
 * - Losing the renderer process no longer ends the app. The WebView is keyed to
 *   [WebViewHost.generation]; the loss advances it, which disposes the dead WebView and
 *   builds a replacement for the same tab.
 * - After a crash the error state stays up and the replacement stays empty until Reload.
 *   After the system reclaimed the renderer, the page reloads straight away.
 * - The replacement starts with no back/forward history.
 */
@Composable
@Suppress("LongParameterList") // Spec 021 adds the adopted pop-up WebView.
internal fun BrowserWebView(
    state: BrowserUiState,
    actions: WebViewActionsHandle,
    callbacks: BrowserWebViewCallbacks,
    navigationCallbacks: BrowserNavigationCallbacks,
    modifier: Modifier = Modifier,
    adopted: WebView? = null,
    textZoomPercent: Int = BrowserLimits.TEXT_SIZE_PERCENT_100,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Spec 021 research R5 — a page-created WebView is adopted by the first generation only; a
    // replacement after a renderer loss is built fresh.
    val adoptable = remember { arrayOf(adopted) }
    val currentUrl = rememberUpdatedState(state.currentUrl)
    val host = remember { WebViewHost(currentUrl) }
    // Spec 022 FR-031 — the text size, applied before a WebView's first load and then to the live
    // one whenever it changes, without a reload. A tab that is not showing has no WebView; it gets
    // the current size when it is shown.
    val textZoom = rememberUpdatedState(textZoomPercent)
    LaunchedEffect(textZoomPercent) { host.current?.settings?.textZoom = textZoomPercent }

    // Lifecycle: pause WebView when host stops, resume when started.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> host.current?.onPause()
                Lifecycle.Event.ON_RESUME -> host.current?.onResume()
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

    // Renderer recovery — each generation owns one WebView. When its renderer process goes
    // away, the host moves to the next generation, which disposes the dead WebView and builds
    // a replacement.
    key(host.generation) {
        val generation = host.generation
        // This generation's own WebView: by the time its cleanup runs after a renderer loss,
        // `host.current` no longer points at it.
        val instance = remember { arrayOfNulls<WebView>(1) }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                val toAdopt = adoptable[0]
                adoptable[0] = null
                buildConfiguredWebView(
                    webView = (toAdopt ?: WebView(context)).apply { settings.textZoom = textZoom.value },
                    adopted = toAdopt != null,
                    state = state,
                    actions = actions,
                    callbacks = callbacks,
                    navigationCallbacks = navigationCallbacks,
                    host = host,
                ).also { wv ->
                    instance[0] = wv
                    host.current = wv
                }
            },
        )

        // Native-resource cleanup. Keyed to Unit so this fires only once per generation, on
        // disposal (NOT every recomposition). See research.md R2 and [releaseWebView].
        DisposableEffect(Unit) {
            onDispose {
                instance[0]?.let { wv ->
                    releaseWebView(wv, isIncognito, rendererGone = generation != host.generation)
                    if (host.current === wv) host.current = null
                }
                instance[0] = null
            }
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
    webView: WebView,
    adopted: Boolean,
    state: BrowserUiState,
    actions: WebViewActionsHandle,
    callbacks: BrowserWebViewCallbacks,
    navigationCallbacks: BrowserNavigationCallbacks,
    host: WebViewHost,
): WebView = webView.also { wv ->
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
        onRendererGone = { crashed ->
            // This WebView must never be used again: silence the handle until the
            // replacement re-wires it.
            actions.detach()
            // The replacement starts without this WebView's back/forward history.
            navigationCallbacks.onCanGoBackChange(false)
            navigationCallbacks.onCanGoForwardChange(false)
            if (crashed) callbacks.onLoadFailed(ErrorReason.Generic)
            host.replaceAfterRendererGone(crashed)
        },
        pageRequests = callbacks.pageRequests,
    )
    // Spec 015 FR-001 — the engine hands off anything it will not render. Attached beside
    // the two platform clients because this is the same shape of event they carry, and the
    // incognito flag is in scope here yet deliberately NOT consulted: FR-014a records an
    // incognito download exactly like any other, so there is no branch to add.
    wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        callbacks.onDownloadRequested(url, userAgent, contentDisposition, mimeType)
    }
    // Spec 022 US1 — find-in-page results go to the find bar's state.
    wv.setFindListener { activeOrdinal, total, _ -> callbacks.onFindResult(activeOrdinal, total) }
    // Spec 021 US4 — the long-press menu on links and images; anything else keeps selection.
    wv.setOnLongClickListener { onLongPress(wv, callbacks.pageRequests) }
    // Spec 019 R7 — feed the sliding bars. API 23+, below minSdk 24.
    wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        navigationCallbacks.onScrollDelta((scrollY - oldScrollY).toFloat())
    }
    wv.webChromeClient = BrowserChromeClient(
        onProgressChanged = callbacks.onProgressChanged,
        onReceivedTitle = navigationCallbacks.onTitleChange,
        onReceivedIcon = navigationCallbacks.onIconReceived,
        pageRequests = callbacks.pageRequests,
        newPopupWebView = { context -> newPopupWebView(context, state.isIncognito) },
    )
    wireActions(wv, actions, host, adopted)

    // Spec 008 — conditional initial load. When state is seeded to Failed
    // (instrumented test pattern), skip the initial loadUrl so the test
    // assertion on the error UI is not overridden by a successful auto-load.
    // Renderer recovery — also skipped by the replacement for a crashed renderer, so a page
    // that crashes its renderer waits for Reload instead of crashing it again in a loop.
    // Spec 021 R5 — and by an adopted pop-up, already navigating for its opener.
    val isHome = state.currentUrl == UrlConstants.HOME_PAGE_URL
    val loadable = host.loadInitialUrl && state.loadingState !is LoadingState.Failed
    if (!adopted && loadable && !isHome) {
        wv.loadUrl(state.currentUrl)
    }
}

/**
 * Spec 008 — wires the imperative handle. Each lambda captures [wv]; they persist for the
 * lifetime of the WebView.
 */
private fun wireActions(wv: WebView, actions: WebViewActionsHandle, host: WebViewHost, adopted: Boolean) {
    actions.goBack = { if (wv.canGoBack()) wv.goBack() }
    actions.goForward = { if (wv.canGoForward()) wv.goForward() }
    // A replacement built after a renderer crash has loaded nothing, and `reload()` on an
    // empty WebView does nothing — so Reload loads the tab's current address instead.
    actions.reload = {
        when {
            wv.url != null -> wv.reload()
            host.currentUrl != UrlConstants.HOME_PAGE_URL -> wv.loadUrl(host.currentUrl)
        }
    }
    actions.stopLoading = { wv.stopLoading() }
    // Spec 022 US1 — find in page, on the WebView's own search (research R3).
    actions.findAll = { text -> wv.findAllAsync(text) }
    actions.findNext = { forward -> wv.findNext(forward) }
    actions.clearMatches = { wv.clearMatches() }
    // Spec 019 R1 — the reserved Home address is never handed to the WebView.
    // Spec 021 R5 — an adopted pop-up is already navigating for its opener: the tab switch's
    // load of the placeholder address is skipped once.
    var skipNextLoad = adopted
    actions.loadUrl = { url ->
        when {
            skipNextLoad -> skipNextLoad = false
            url != UrlConstants.HOME_PAGE_URL -> wv.loadUrl(url)
        }
    }
}

/** Spec 021 research R5 — a WebView with the browser's settings for a window the page opened. */
private fun newPopupWebView(context: android.content.Context, incognito: Boolean): WebView =
    WebView(context).also { popup ->
        applySecuritySettings(popup)
        if (incognito) applyIncognitoSettings(popup)
        // Until its tab adopts it, the pop-up must load in place: a WebView with no client hands
        // every navigation to another app (the system browser).
        popup.webViewClient = PendingPopupWebViewClient()
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

        // Spec 021 FR-001 (found at G1 on a Galaxy A55) — with the WebView default, a camera
        // stream attached to a <video> never starts: autoplay needs a fresh tap, and the
        // permission dialog outlives the tap. Video calls would show nothing. Mainstream
        // browsers let such media start on their own; so does this one.
        mediaPlaybackRequiresUserGesture = false

        // Spec 021 research R5 — every window request reaches onCreateWindow, which decides.
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true

        // Spec 021 FR-001 — location requests reach the app, which asks the person first.
        setGeolocationEnabled(true)
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
 *
 * Renderer recovery — [onRenderProcessGone] reports the loss of the renderer process through
 * [onRendererGone] instead of letting it end the app.
 */
@Suppress("LongParameterList")
// androidx.webkit's `MissingOnRenderProcessGone` check has two halves. One looks for an
// `onRenderProcessGone` override, which this class has. The other reports every call to the
// `WebViewClient` constructor, and in Kotlin that includes the superclass call in this class
// header, so no implementation can clear it. Suppressed for that half only.
@android.annotation.SuppressLint("MissingOnRenderProcessGone")
private class BrowserWebViewClient(
    private val onLoadStarted: (String) -> Unit,
    private val onLoadFinished: (String) -> Unit,
    private val onLoadFailed: (ErrorReason) -> Unit,
    private val onUrlChange: (String) -> Unit,
    private val onCanGoBackChange: (Boolean) -> Unit,
    private val onCanGoForwardChange: (Boolean) -> Unit,
    private val onScreenshotReady: (Bitmap) -> Unit,
    private val onRendererGone: (crashed: Boolean) -> Unit,
    private val pageRequests: PageRequestSink,
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
            // Spec 021 FR-036 — the address is not logged.
            android.util.Log.w("BrowserWebView", "External intent dispatch failed", t)
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        // Spec 021 FR-034 — a new page: the previous page's requests are now stale.
        pageRequests.onNavigationStarted()
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

    /**
     * Spec 021 FR-032 — basic/digest sign-in. The typed name and password go to this one request
     * only: never stored, never `setHttpAuthUsernamePassword` (INV-5).
     */
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        handler ?: return
        val context = pageRequests.context()
        if (context == null || host.isNullOrBlank()) {
            handler.cancel()
            return
        }
        pageRequests.enqueue(
            PageRequest.HttpAuth(
                tabId = context.tabId,
                generation = context.generation,
                origin = host,
                host = host,
                realm = realm?.takeIf { it.isNotBlank() },
                onProceed = { username, password -> handler.proceed(username, password) },
                onCancel = { handler.cancel() },
            ),
        )
    }

    /**
     * The renderer process behind [view] is gone: it crashed, or the system reclaimed it for
     * memory.
     *
     * Returning `false`, the platform default, ends the whole app and every tab with it.
     * Returning `true` promises [view] is never used again: [onRendererGone] detaches it, and
     * [BrowserWebView] then disposes it and builds a replacement. After a crash the replacement
     * waits for Reload, so a page that crashes its renderer cannot keep doing so; after the
     * system reclaimed the renderer, the replacement reloads the page by itself.
     *
     * Called on API 26+ only. Below that the renderer runs inside the app's own process, so a
     * renderer crash is an app crash and there is nothing left to recover.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // Unknown counts as a crash: the cautious choice is not to reload the page automatically.
        val crashed = detail?.didCrash() ?: true
        // No address in the log line: it would write browsing activity to logcat.
        android.util.Log.w("BrowserWebView", "Renderer process gone (crashed=$crashed); replacing the WebView")
        onRendererGone(crashed)
        return true
    }
}

/**
 * Renderer-recovery state for one [BrowserWebView], that is, one tab activation. It outlives
 * the WebViews it replaces.
 */
private class WebViewHost(private val currentUrlState: androidx.compose.runtime.State<String>) {
    /** The WebView that lifecycle events act on, or null while none is attached. */
    var current: WebView? = null

    /** Advances each time a renderer goes away, which re-keys [BrowserWebView]'s WebView. */
    var generation by mutableIntStateOf(0)
        private set

    /** Whether the next WebView built loads the page by itself. False after a renderer crash. */
    var loadInitialUrl: Boolean = true
        private set

    /** The tab's live address, so a replacement loads the page the tab is actually on. */
    val currentUrl: String get() = currentUrlState.value

    fun replaceAfterRendererGone(crashed: Boolean) {
        current = null
        loadInitialUrl = !crashed
        generation++
    }
}

/**
 * Releases a WebView when its [BrowserWebView] generation leaves composition.
 *
 * Spec 007 (research.md R2) — `stopLoading` + `loadUrl("about:blank")` + `removeAllViews` +
 * `destroy` avoids the WebView 116+ native-resource race.
 *
 * Spec 012 R5 — incognito tabs run a 9-step teardown that prepends 5 clear*() calls before the
 * Spec 007 4-step destroy, ensuring no per-WebView state (history, form data, find-on-page, SSL
 * decisions, disk cache) outlives the close-tab moment. Order is fixed; each clear* is
 * idempotent so re-entry under stress is harmless.
 *
 * Renderer recovery — once [rendererGone], the platform forbids any further use of the WebView,
 * so it is only destroyed; its history, form data and find-on-page matches go with it. The
 * replacement belongs to the same tab and runs the full sequence when it is released, which is
 * when an incognito tab's SSL decisions and disk cache are cleared.
 */
private fun releaseWebView(webView: WebView, isIncognito: Boolean, rendererGone: Boolean) {
    if (rendererGone) {
        webView.destroy()
        return
    }
    webView.stopLoading()
    if (isIncognito) {
        webView.clearHistory()
        webView.clearFormData()
        webView.clearMatches()
        webView.clearSslPreferences()
        // includeDiskFiles = true → wipe disk cache too.
        webView.clearCache(true)
    }
    webView.loadUrl(BLANK_PAGE)
    webView.removeAllViews()
    webView.destroy()
}

/**
 * Spec 021 research R7 — reads what was pressed. An image inside a link carries the image address;
 * the link address arrives asynchronously from `requestFocusNodeHref`. Returns `false` when there
 * is no menu, so the WebView's text selection runs instead.
 */
private fun onLongPress(webView: WebView, pageRequests: PageRequestSink): Boolean {
    val hit = webView.hitTestResult
    val type = when (hit.type) {
        WebView.HitTestResult.SRC_ANCHOR_TYPE -> LongPressHit.Link
        WebView.HitTestResult.IMAGE_TYPE -> LongPressHit.Image
        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> LongPressHit.ImageLink
        else -> null
    }
    val context = pageRequests.context()
    if (type == null || context == null) return false
    val origin = SiteOrigin.of(webView.url.orEmpty()).orEmpty()
    val enqueue: (String?, String?) -> Unit = { link, image ->
        BuildLongPressMenuUseCase()(type, link, image)?.let { menu ->
            pageRequests.enqueue(PageRequest.LongPress(context.tabId, context.generation, origin, menu) {})
        }
    }
    when (type) {
        LongPressHit.Link -> enqueue(hit.extra, null)
        LongPressHit.Image -> enqueue(null, hit.extra)
        LongPressHit.ImageLink -> {
            val handler = Handler(Looper.getMainLooper()) { message ->
                enqueue(message.data.getString(FOCUS_NODE_HREF_URL), hit.extra)
                true
            }
            webView.requestFocusNodeHref(handler.obtainMessage())
        }
    }
    return true
}

/** Key of the link address in `requestFocusNodeHref`'s reply. */
private const val FOCUS_NODE_HREF_URL: String = "url"

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
