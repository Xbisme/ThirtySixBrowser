package com.raumanian.thirtysix.browser.presentation.browser.page

/** Spec 021 — which page a request belongs to (data-model §3, §4). */
data class PageContext(val tabId: Long, val generation: Long, val isIncognito: Boolean)

/**
 * Spec 021 research R1 — how the WebView clients hand page requests to the browser screen. The
 * clients translate platform callbacks; everything that involves the person happens behind this
 * interface. [None] is the inert default for previews and tests that build the WebView directly.
 */
interface PageRequestSink {
    /** The page currently shown, or `null` when there is none. */
    fun context(): PageContext?

    /** A new main-frame navigation started: the previous page's requests are stale (FR-034). */
    fun onNavigationStarted()

    /** Queues [request] for the person. */
    fun enqueue(request: PageRequest)

    /**
     * Spec 021 research R2 — a camera / microphone / location request: decided silently when a
     * decision exists, otherwise queued for the person. [hasUnsupported] → refused (FR-008).
     */
    fun onPermissionRequest(request: PageRequest.Permission, hasUnsupported: Boolean)

    /** The page withdrew permission requests matching [match] before they were answered. */
    fun onPermissionWithdrawn(match: (PageRequest.Permission) -> Boolean) = Unit

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
