@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintManager
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
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
import com.raumanian.thirtysix.browser.core.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.presentation.util.findActivity
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressHit
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.BuildLongPressMenuUseCase
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageRequest
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageRequestSink
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PendingPopupWebViewClient
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.WebViewProtection
/**
 * Compose wrapper around [android.webkit.WebView], locked down: no JavaScript bridge, no file
 * access, no mixed content. A lost renderer re-keys the WebView instead of ending the app.
 */
@Composable
@Suppress("LongParameterList") // Host state plus an adopted pop-up WebView.
internal fun BrowserWebView(
    state: BrowserUiState,
    actions: WebViewActionsHandle,
    callbacks: BrowserWebViewCallbacks,
    navigationCallbacks: BrowserNavigationCallbacks,
    incognitoProfile: IncognitoWebProfile,
    modifier: Modifier = Modifier,
    adopted: WebView? = null,
    textZoomPercent: Int = BrowserLimits.TEXT_SIZE_PERCENT_100,
    desktopMode: Boolean = false,
    protection: ProtectionHooks? = null,
    session: TabSessionHooks? = null,
    swipe: SwipeNavigationHooks? = null,
) {
    // A page-created WebView is adopted by the first generation only; a renderer-loss replacement is built fresh.
    val adoptable = remember { arrayOf(adopted) }
    val currentUrl = rememberUpdatedState(state.currentUrl)
    val host = remember { WebViewHost(currentUrl) }
    // Text size is applied before the first load and then live, without a reload.
    val textZoom = rememberUpdatedState(textZoomPercent)
    LaunchedEffect(textZoomPercent) { host.current?.settings?.textZoom = textZoomPercent }

    PauseWithLifecycle(shownWebView = { host.current }, onStop = { wv -> session?.saveNow(wv) })

    val isIncognito = state.isIncognito

    // Renderer recovery: each generation owns one WebView; a lost renderer advances the generation.
    key(host.generation) {
        val generation = host.generation
        val instance = remember { arrayOfNulls<WebView>(1) }
        val guard = remember { arrayOfNulls<WebViewProtection>(1) }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                val toAdopt = adoptable[0].also { adoptable[0] = null }
                // An incognito WebView gets the session's own profile before anything else touches it.
                val fresh = toAdopt
                    ?: WebViewProfiles.prepare(BrowserPageView(context), state.isIncognito, incognitoProfile)
                val webView = preparePageTools(fresh, actions, textZoom.value, desktopMode)
                    // Without explicit params AndroidView lays the WebView out as WRAP_CONTENT, and
                    // Chromium then gives the page a layout viewport 0 px tall: pages sized with
                    // `height: 100%` (maps, drawing apps) render blank. The modifier still sizes it.
                    .apply { layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT) }
                guard[0] = protection?.protect(webView, state.isIncognito)
                buildConfiguredWebView(
                    webView = webView,
                    adopted = toAdopt != null,
                    state = state,
                    actions = actions,
                    callbacks = callbacks,
                    navigationCallbacks = navigationCallbacks,
                    host = host,
                    protection = guard[0],
                    incognitoProfile = incognitoProfile,
                    session = session,
                ).also { wv ->
                    (wv as? BrowserPageView)?.swipe = swipe
                    instance[0] = wv
                    host.current = wv
                }
            },
        )

        // Keyed to Unit: runs once per generation, on disposal, not on recomposition.
        DisposableEffect(Unit) {
            onDispose {
                guard[0]?.let { protection?.unprotect(it) }
                guard[0] = null
                instance[0]?.let { wv ->
                    val rendererGone = generation != host.generation
                    if (!rendererGone) session?.saveNow(wv)
                    actions.detachFrom(wv)
                    releaseWebView(wv, isIncognito, rendererGone)
                    if (host.current === wv) host.current = null
                }
                instance[0] = null
            }
        }
    }
}

/** The page tools' WebView side, set up before the first load: text size, page mode, desktop mode and print. */
private fun preparePageTools(
    webView: WebView,
    actions: WebViewActionsHandle,
    textZoomPercent: Int,
    desktopMode: Boolean,
): WebView = webView.apply {
    settings.textZoom = textZoomPercent
    val pageMode = DesktopModeController(this)
    pageMode.apply(desktopMode)
    actions.setDesktopMode = { desktop ->
        stopLoading()
        pageMode.apply(desktop)
        actions.reload()
    }
    // Nothing about the print job is recorded. PrintManager needs an activity context, which the WebView has.
    actions.print = { jobName ->
        runCatching {
            val printManager = context.getSystemService(PrintManager::class.java)
            printManager.print(jobName, createPrintDocumentAdapter(jobName), null)
        }
    }
}

@Suppress("LongParameterList")
private fun buildConfiguredWebView(
    webView: WebView,
    adopted: Boolean,
    state: BrowserUiState,
    actions: WebViewActionsHandle,
    callbacks: BrowserWebViewCallbacks,
    navigationCallbacks: BrowserNavigationCallbacks,
    host: WebViewHost,
    protection: WebViewProtection?,
    incognitoProfile: IncognitoWebProfile,
    session: TabSessionHooks?,
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
            // This WebView must never be used again: silence the handle until the replacement re-wires it.
            actions.detach()
            navigationCallbacks.onCanGoBackChange(false)
            navigationCallbacks.onCanGoForwardChange(false)
            if (crashed) callbacks.onLoadFailed(ErrorReason.Generic)
            host.replaceAfterRendererGone(crashed)
        },
        pageRequests = callbacks.pageRequests,
        protection = protection,
        onHistoryCommitted = { session?.saveSoon(wv) },
        onPageVisible = callbacks.onPageVisible,
    )
    // Incognito downloads are recorded like any other, so the incognito flag is deliberately not consulted.
    wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        callbacks.onDownloadRequested(url, userAgent, contentDisposition, mimeType)
    }
    wv.setFindListener { activeOrdinal, total, _ -> callbacks.onFindResult(activeOrdinal, total) }
    // `clearHistory` reports no history change, so Back and Forward are switched off here.
    actions.clearHistory = {
        wv.clearHistory()
        navigationCallbacks.onCanGoBackChange(false)
        navigationCallbacks.onCanGoForwardChange(false)
    }
    // Long-press menu on links and images; anything else keeps text selection.
    wv.setOnLongClickListener { onLongPress(wv, callbacks.pageRequests) }
    wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        navigationCallbacks.onScrollDelta((scrollY - oldScrollY).toFloat())
    }
    wv.webChromeClient = BrowserChromeClient(
        onProgressChanged = callbacks.onProgressChanged,
        onReceivedTitle = navigationCallbacks.onTitleChange,
        onReceivedIcon = navigationCallbacks.onIconReceived,
        pageRequests = callbacks.pageRequests,
        incognitoProfile = incognitoProfile,
        newPopupWebView = { context -> newPopupWebView(context, state.isIncognito, incognitoProfile) },
    )
    // No initial load when the state is seeded to Failed (keeps the error UI), after a renderer crash
    // (so a crashing page waits for Reload instead of looping), or for an adopted pop-up (already navigating).
    val isHome = state.currentUrl == UrlConstants.HOME_PAGE_URL
    val loadable = host.loadInitialUrl && state.loadingState !is LoadingState.Failed
    // A restored tab reloads its own current page, with this client and the protections already attached.
    val restored = !adopted && loadable && session?.restore(wv) == true
    wireActions(wv, actions, host, skipFirstLoad = adopted || restored, protection)
    val needsLoad = !adopted && !restored
    if (needsLoad && loadable && !isHome) {
        wv.loadProtected(state.currentUrl, protection)
    }
}

private fun wireActions(
    wv: WebView,
    actions: WebViewActionsHandle,
    host: WebViewHost,
    skipFirstLoad: Boolean,
    protection: WebViewProtection?,
) {
    actions.attachTo(wv)
    actions.goBack = { if (wv.canGoBack()) wv.goBack() }
    actions.goForward = { if (wv.canGoForward()) wv.goForward() }
    // `reload()` on an empty WebView (after a renderer crash) does nothing, so load the tab's address instead.
    actions.reload = {
        when {
            wv.url != null -> wv.reload()
            host.currentUrl != UrlConstants.HOME_PAGE_URL -> wv.loadProtected(host.currentUrl, protection)
        }
    }
    actions.stopLoading = { wv.stopLoading() }
    actions.findAll = { text -> wv.findAllAsync(text) }
    actions.findNext = { forward -> wv.findNext(forward) }
    actions.clearMatches = { wv.clearMatches() }
    // The reserved Home address is never handed to the WebView. An adopted pop-up, already navigating,
    // and a restored tab, already reloading its page, skip the tab switch's load once.
    var skipNextLoad = skipFirstLoad
    actions.loadUrl = { url ->
        when {
            skipNextLoad -> skipNextLoad = false
            url != UrlConstants.HOME_PAGE_URL -> wv.loadProtected(url, protection)
        }
    }
}

/** Every address the app itself loads goes through the tracker and HTTPS protections. */
private fun WebView.loadProtected(url: String, protection: WebViewProtection?) {
    if (protection == null) loadUrl(url) else protection.load(this, url)
}

/** A WebView with the browser's settings for a window the page opened. */
private fun newPopupWebView(
    context: android.content.Context,
    incognito: Boolean,
    incognitoProfile: IncognitoWebProfile,
): WebView =
    WebViewProfiles.prepare(BrowserPageView(context), incognito, incognitoProfile).also { popup ->
        applySecuritySettings(popup)
        if (incognito) applyIncognitoSettings(popup)
        // Until its tab adopts it, the pop-up must load in place: a WebView with no client hands
        // every navigation to another app (the system browser).
        popup.webViewClient = PendingPopupWebViewClient()
    }

/** Cookies keep the system default (accepted and persisted on device); clearing is the user's choice in Settings. */
@Suppress("DEPRECATION")
// The file-URL flags are deprecated but still honoured by older WebView versions on minSdk 24 devices.
private fun applySecuritySettings(webView: WebView) {
    with(webView.settings) {
        @Suppress("SetJavaScriptEnabled") // The modern web needs JavaScript.
        javaScriptEnabled = true
        domStorageEnabled = true // localStorage / sessionStorage; local-only, no upload.

        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false

        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // With the default, a camera stream in a <video> never starts: the permission dialog outlives
        // the tap that autoplay needs, so video calls show nothing.
        mediaPlaybackRequiresUserGesture = false

        // Every window request reaches onCreateWindow, which decides.
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true

        // Location requests reach the app, which asks the person first.
        setGeolocationEnabled(true)
        // No addJavascriptInterface: pages get no bridge into the app.
    }
}

/** Incognito-only lockdown on top of [applySecuritySettings]: no form data saved, no disk cache. */
@Suppress("DEPRECATION")
// setSaveFormData is deprecated since API 26 but still honoured on API 24–25.
private fun applyIncognitoSettings(webView: WebView) {
    with(webView.settings) {
        saveFormData = false
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
}

/**
 * Forwards page lifecycle and main-frame failures to the view model; sub-frame errors are not
 * shown. SSL errors are always cancelled, never proceeded.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One override per WebView callback.
// androidx.webkit's `MissingOnRenderProcessGone` also reports every `WebViewClient` constructor
// call, including this class's own superclass call; the override exists, so that half is suppressed.
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
    private val protection: WebViewProtection?,
    private val onHistoryCommitted: () -> Unit,
    private val onPageVisible: () -> Unit,
) : WebViewClient() {

    /**
     * Runs on a WebView IO thread. Main-frame requests are never blocked; listed third-party
     * requests get an empty response.
     */
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
        request?.let { protection?.intercept(it) }

    /** Non-web schemes go to other apps; a malformed URI or missing handler is swallowed instead of crashing. */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    // Generic catch: any platform call here may throw on a misbehaving handler app or malformed URI.
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in WEB_NATIVE_SCHEMES) {
            // A page's own http(s) navigation may be upgraded or cleaned.
            return view != null && protection?.overrideNavigation(view, request) == true
        }
        val context = view?.context ?: return false
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            // The address is not logged.
            android.util.Log.w("BrowserWebView", "External intent dispatch failed", t)
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        pageRequests.onNavigationStarted()
        url?.let { protection?.monitor?.onPageStarted(it) }
        url?.let {
            // URL first, so the address bar mirrors the (redirected) URL on the tick the load starts.
            onUrlChange(it)
            onLoadStarted(it)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        onPageVisible()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        // A failed HTTPS attempt's error page is not a visit: the http:// page replacing it reports its own events.
        if (view != null && protection?.onPageFinished(view, url) == true) return
        url?.let(onLoadFinished)
        onHistoryCommitted()
        // Capture the tab preview once the page has had time to paint.
        val v = view ?: return
        v.postDelayed({ captureScreenshot(v) }, SCREENSHOT_DELAY_MS)
    }

    // Copied from the screen, never `view.draw()`: a software draw of the WebView leaves the page's own
    // scrollers (carousels, maps) unable to take a drag after the next back or forward navigation.
    // PixelCopy needs API 26; older devices keep the placeholder preview.
    private fun captureScreenshot(view: WebView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val window = view.context.findActivity()?.window
        val area = previewArea(view)
        val source = area?.let { newPreviewBitmap(it.width(), it.height()) }
        if (window == null || source == null) return
        val onCopied = PixelCopy.OnPixelCopyFinishedListener { result ->
            if (result == PixelCopy.SUCCESS) {
                val scaled = source.scale(
                    AppConstants.SCREENSHOT_TARGET_WIDTH_PX,
                    AppConstants.SCREENSHOT_TARGET_HEIGHT_PX,
                    filter = true,
                )
                if (scaled !== source) source.recycle()
                onScreenshotReady(scaled)
            } else {
                source.recycle()
            }
        }
        try {
            PixelCopy.request(window, area, source, onCopied, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            // A window being rebuilt, as on rotation, has no surface yet: this preview is skipped.
            android.util.Log.w("BrowserWebView", "Screenshot skipped", e)
            source.recycle()
        }
    }

    /** The top of the WebView cropped to 16:9 in window coordinates, so a portrait surface is not squashed. */
    private fun previewArea(view: WebView): Rect? {
        if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return null
        val cropHeight = (
            (view.width.toLong() * AppConstants.SCREENSHOT_TARGET_HEIGHT_PX) / AppConstants.SCREENSHOT_TARGET_WIDTH_PX
            ).toInt().coerceIn(1, view.height)
        val origin = IntArray(2).also(view::getLocationInWindow)
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + cropHeight)
    }

    private fun newPreviewBitmap(width: Int, height: Int): Bitmap? = try {
        androidx.core.graphics.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        // Large surface, low memory: the tab keeps its placeholder preview.
        android.util.Log.w("BrowserWebView", "Screenshot OOM", e)
        null
    }

    // Fires after every history commit, including fragment and pushState navigations.
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val v = view ?: return
        url?.let(onUrlChange)
        onCanGoBackChange(v.canGoBack())
        onCanGoForwardChange(v.canGoForward())
        onHistoryCommitted()
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame != true || error == null) return
        // A failed HTTPS upgrade falls back to http:// without an error.
        if (protection?.fallBackIfUpgrade(request.url.toString()) == true) return
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
        // Sub-resource errors (including blocked responses) never reach the error UI; an HTTP error is
        // the site's answer, never a reason to fall back to http://.
        if (request?.isForMainFrame != true || errorResponse == null) return
        onLoadFailed(ErrorReason.HttpError(errorResponse.statusCode))
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        handler?.cancel()
        // A certificate failure on an HTTPS upgrade falls back to http://.
        if (protection?.fallBackIfUpgrade(error?.url) == true) return
        onLoadFailed(ErrorReason.SslError)
    }

    /** Basic/digest sign-in: credentials go to this one request only and are never stored. */
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
     * Returning `true` keeps the app alive: [view] is detached, disposed and replaced. Called on
     * API 26+ only; below that the renderer shares the app process.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // Unknown counts as a crash: the cautious choice is not to reload the page automatically.
        val crashed = detail?.didCrash() ?: true
        android.util.Log.w("BrowserWebView", "Renderer process gone (crashed=$crashed); replacing the WebView")
        onRendererGone(crashed)
        return true
    }
}

/** Renderer-recovery state for one tab activation; it outlives the WebViews it replaces. */
private class WebViewHost(private val currentUrlState: androidx.compose.runtime.State<String>) {
    var current: WebView? = null

    var generation by mutableIntStateOf(0)
        private set

    var loadInitialUrl: Boolean = true
        private set

    val currentUrl: String get() = currentUrlState.value

    fun replaceAfterRendererGone(crashed: Boolean) {
        current = null
        loadInitialUrl = !crashed
        generation++
    }
}

/**
 * Releases a WebView when its generation leaves composition. The blank load before `destroy`
 * avoids a native-resource race; incognito tabs clear their per-WebView state first. A WebView
 * whose renderer is gone may only be destroyed.
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
        webView.clearCache(true)
    }
    webView.loadUrl(BLANK_PAGE)
    webView.removeAllViews()
    webView.destroy()
}

/** Opens the long-press menu for a link or image; `false` lets the WebView's text selection run. */
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

private const val FOCUS_NODE_HREF_URL: String = "url"

private const val BLANK_PAGE: String = "about:blank"

/** Schemes the WebView loads itself; anything else is handed to another app. */
private val WEB_NATIVE_SCHEMES: Set<String> = setOf("http", "https", "about", "data")

// Gives the page time to paint (fade-ins, progressive images) before the preview is captured.
private const val SCREENSHOT_DELAY_MS: Long = 200L
