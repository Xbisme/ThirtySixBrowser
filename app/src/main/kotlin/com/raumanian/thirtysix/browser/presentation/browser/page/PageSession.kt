package com.raumanian.thirtysix.browser.presentation.browser.page

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebView
import com.raumanian.thirtysix.browser.domain.usecase.PopupDecision
import com.raumanian.thirtysix.browser.domain.usecase.SitePermissionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Spec 021 data-model §4 — the page the browser screen is showing, and its generation: a number
 * that moves on every new main-frame navigation and every tab switch. Requests, one-time grants
 * and dialog suppression are tied to it, so anything belonging to a page that is gone is dropped
 * (FR-034).
 *
 * Owned by the browser screen, used on the main thread only.
 */
@Suppress("TooManyFunctions") // One entry point per kind of page request (C3.1).
class PageSession(
    val queue: PageRequestQueue,
    private val scope: CoroutineScope? = null,
    private val permissionGate: SitePermissionGate? = null,
    private val popups: PopupTabs? = null,
) : PageRequestSink {
    /** Spec 021 research R4 — the page's fullscreen view, ended by a tab switch. */
    val fullscreen = FullscreenController()

    private var tabId: Long? = null
    private var isIncognito: Boolean = false
    private var generation: Long = 0L

    override fun context(): PageContext? = tabId?.let { PageContext(it, generation, isIncognito) }

    /** The screen now shows [newTabId]. */
    fun onTabChanged(newTabId: Long?, incognito: Boolean) {
        val previous = context()
        if (newTabId != tabId) fullscreen.exit()
        tabId = newTabId
        isIncognito = incognito
        advance(previous)
    }

    override fun onNavigationStarted() = advance(context())

    override fun enqueue(request: PageRequest) {
        val current = context()
        if (current == null || request.tabId != current.tabId || request.generation != current.generation) {
            request.cancel()
        } else {
            queue.enqueue(request)
        }
    }

    override fun onPermissionRequest(request: PageRequest.Permission, hasUnsupported: Boolean) {
        val gate = permissionGate
        val launchScope = scope
        if (gate == null || launchScope == null) {
            request.deny()
            return
        }
        launchScope.launch {
            when (val outcome = gate.resolve(request, hasUnsupported)) {
                SitePermissionOutcome.Grant ->
                    if (gate.holdsSystemPermissions(request.capabilities)) {
                        request.grant()
                    } else {
                        request.needsSystemPermissionOnly = true
                        enqueue(request)
                    }
                SitePermissionOutcome.Deny -> request.deny()
                is SitePermissionOutcome.Ask -> {
                    request.askedCapabilities = outcome.capabilities
                    enqueue(request)
                }
            }
        }
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = fullscreen.enter(view, callback)

    override fun onHideCustomView() = fullscreen.onHiddenByPage()

    override fun decideWindow(isUserGesture: Boolean): PopupDecision? {
        val opener = context() ?: return null
        return popups?.decide(isUserGesture, opener)
    }

    override fun adoptPopup(popup: WebView) {
        val opener = context()
        val tabs = popups
        if (opener == null || tabs == null) popup.destroy() else tabs.adopt(popup, opener)
    }

    override fun onPopupBlocked(target: String?) {
        context()?.let { popups?.onBlocked(target, it) }
    }

    override fun onCloseWindow(window: WebView) {
        popups?.onCloseWindow(window)
    }

    override fun onPermissionWithdrawn(match: (PageRequest.Permission) -> Boolean) = queue.withdraw(match)

    private fun advance(previous: PageContext?) {
        generation += 1
        queue.dropStale(tabId, generation)
        previous?.let { permissionGate?.onPageChanged(it.tabId) }
    }
}
