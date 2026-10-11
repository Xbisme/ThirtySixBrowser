package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.presentation.browser.page.FileChooserRequest
import com.raumanian.thirtysix.browser.presentation.browser.page.PageContext
import com.raumanian.thirtysix.browser.presentation.browser.page.PageRequest
import com.raumanian.thirtysix.browser.presentation.browser.page.PageRequestSink

/**
 * Forwards progress, page title (Spec 011 / M1) and favicon (Spec 011 favicon amendment), and
 * translates the page's requests into [PageRequest]s for the browser screen (Spec 021 C3.1).
 */
@Suppress("TooManyFunctions") // One override per platform hook (Spec 021 C3.1); each only translates.
internal class BrowserChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onReceivedTitle: (String) -> Unit,
    private val onReceivedIcon: (String, Bitmap) -> Unit,
    private val pageRequests: PageRequestSink,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        title?.let(onReceivedTitle)
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        val url = view?.url ?: return
        if (icon != null) onReceivedIcon(url, icon)
    }

    /** Spec 021 FR-001 / FR-008 — camera and microphone; every other resource is refused. */
    override fun onPermissionRequest(request: PermissionRequest?) {
        request ?: return
        val context = pageRequests.context()
        val origin = SiteOrigin.of(request.origin.toString())
        if (context == null || origin == null) {
            request.deny()
            return
        }
        val supported = request.resources.mapNotNull(::capabilityOf)
        val granted = request.resources.filter { capabilityOf(it) != null }.toTypedArray()
        pageRequests.onPermissionRequest(
            PageRequest.Permission(
                tabId = context.tabId,
                generation = context.generation,
                origin = origin,
                capabilities = supported.toSet(),
                isIncognito = context.isIncognito,
                onGrant = { request.grant(granted) },
                onDeny = { request.deny() },
                token = request,
            ),
            hasUnsupported = supported.size != request.resources.size,
        )
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        request ?: return
        pageRequests.onPermissionWithdrawn { it.token === request }
    }

    /** Spec 021 FR-001 — location; never retained by the engine, the app keeps its own decision (INV-6). */
    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        callback ?: return
        val context = pageRequests.context()
        val siteOrigin = origin?.let(SiteOrigin::of)
        if (context == null || siteOrigin == null) {
            callback.invoke(origin, false, false)
            return
        }
        pageRequests.onPermissionRequest(
            PageRequest.Permission(
                tabId = context.tabId,
                generation = context.generation,
                origin = siteOrigin,
                capabilities = setOf(SiteCapability.Location),
                isIncognito = context.isIncognito,
                onGrant = { callback.invoke(origin, true, false) },
                onDeny = { callback.invoke(origin, false, false) },
                token = callback,
            ),
            hasUnsupported = false,
        )
    }

    override fun onGeolocationPermissionsHidePrompt() {
        pageRequests.onPermissionWithdrawn { it.capabilities == setOf(SiteCapability.Location) }
    }

    /** Spec 021 FR-011 — a page's file input; the page is answered once, with files or `null`. */
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        val callback = filePathCallback ?: return false
        val context = pageRequests.context()
        val origin = webView?.url?.let(SiteOrigin::of)
        if (context == null || origin == null) {
            callback.onReceiveValue(null)
        } else {
            enqueueFileChooser(context, origin, fileChooserParams, callback)
        }
        return true
    }

    private fun enqueueFileChooser(
        context: PageContext,
        origin: String,
        fileChooserParams: FileChooserParams?,
        callback: ValueCallback<Array<Uri>>,
    ) {
        pageRequests.enqueue(
            PageRequest.FileChooser(
                tabId = context.tabId,
                generation = context.generation,
                origin = origin,
                request = FileChooserRequest(
                    acceptTypes = fileChooserParams?.acceptTypes?.toList().orEmpty(),
                    allowMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                    captureRequested = fileChooserParams?.isCaptureEnabled == true,
                ),
                onResult = { callback.onReceiveValue(it) },
            ),
        )
    }

    /** Spec 021 FR-014 — the page asked for fullscreen (a video, usually). */
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        callback ?: return
        if (view == null) callback.onCustomViewHidden() else pageRequests.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() = pageRequests.onHideCustomView()

    private fun capabilityOf(resource: String): SiteCapability? = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SiteCapability.Camera
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SiteCapability.Microphone
        else -> null
    }
}
