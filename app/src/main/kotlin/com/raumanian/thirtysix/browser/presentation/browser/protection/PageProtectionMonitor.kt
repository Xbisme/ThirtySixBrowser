package com.raumanian.thirtysix.browser.presentation.browser.protection

import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.constants.NetworkTimeouts
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.domain.protection.HttpsUpgradePolicy
import com.raumanian.thirtysix.browser.domain.protection.NavigationPreparer
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spec 023 — the protections' state for one WebView: the page on screen, its blocked trackers and
 * a pending HTTPS upgrade.
 *
 * Threads: [onMainFrameRequest] and [onSubresource] run on WebView IO threads; everything else on
 * the main thread. Nothing here touches the database or blocks the main thread (data-model
 * invariant 2). [status] is read only by the hub (§V rule 3).
 */
@Suppress("TooManyFunctions") // One entry point per WebView callback it follows.
class PageProtectionMonitor internal constructor(
    private val engine: ProtectionEngine,
    val isIncognito: Boolean,
) {
    private data class PendingUpgrade(val host: String, val httpUrl: String)

    private val blocked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile private var topUrl: String? = null

    @Volatile private var topHost: String? = null

    @Volatile private var topSite: String? = null

    @Volatile private var protectionOn: Boolean = true

    /** A main-frame request was seen and its page has not started yet. */
    @Volatile private var awaitingCommit: Boolean = false

    @Volatile private var pending: PendingUpgrade? = null

    private val _status = MutableStateFlow(PageProtectionStatus.NOT_A_WEB_PAGE)
    val status: StateFlow<PageProtectionStatus> = _status.asStateFlow()

    /** The registrable domain of the page on screen, or `null` when no web page is shown. */
    val currentSite: String? get() = topSite

    /**
     * IO thread — a new page load (also a reload) is starting: FR-010's per-page reset happens
     * here, before any of the new page's resources can be blocked.
     */
    fun onMainFrameRequest(url: String) {
        // Off the main thread, so waiting for the lists here is allowed (research R10).
        engine.matchers.awaitMatchers(NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS)
        awaitingCommit = true
        enterPage(url)
    }

    /**
     * Main thread, `onPageStarted`. After redirects the committed address may differ from the
     * requested one: the page keeps its count but follows the new site. A restore from history
     * that issued no request starts a fresh count.
     */
    fun onPageStarted(url: String) {
        if (url == topUrl) {
            awaitingCommit = false
            publish(url)
            return
        }
        if (awaitingCommit) {
            follow(url)
        } else {
            enterPage(url)
        }
        awaitingCommit = false
        publish(url)
    }

    /** IO thread. A blocked response for a listed third-party request, or `null` to let it load. */
    fun onSubresource(requestUrl: Uri): WebResourceResponse? {
        val host = requestUrl.host?.lowercase()
        val top = topHost
        if (host == null || top == null || !shouldBlock(host, top)) return null
        if (blocked.add(host)) _status.value = _status.value.copy(blockedTrackerCount = blocked.size)
        return blockedResponse()
    }

    private fun shouldBlock(host: String, top: String): Boolean =
        engine.matchers.awaitMatchers(NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS)
            ?.trackers?.shouldBlock(host, top, protectionOn) == true

    fun beginUpgrade(prepared: NavigationPreparer.Prepared) {
        val http = prepared.upgradedFrom ?: return
        val host = HttpsUpgradePolicy.hostOf(prepared.url) ?: return
        pending = PendingUpgrade(host, http)
    }

    /**
     * A main-frame load failed (connection, certificate). When it was the pending HTTPS attempt,
     * the host is remembered as failed and the original `http://` address is returned to load
     * instead (FR-019, FR-020); otherwise `null`.
     */
    fun onMainFrameFailure(failedUrl: String?): String? {
        val upgrade = pending?.takeIf { failedUrl != null && isSecureAttempt(it, failedUrl) }
        if (upgrade != null) {
            pending = null
            engine.failures.markFailed(upgrade.host)
        }
        return upgrade?.httpUrl
    }

    private fun isSecureAttempt(upgrade: PendingUpgrade, url: String): Boolean =
        url.startsWith(UrlConstants.HTTPS_SCHEME_PREFIX, ignoreCase = true) &&
            HttpsUpgradePolicy.hostOf(url) == upgrade.host

    /** A redirect: `true` when the pending HTTPS attempt was sent back to `http://` (FR-019). */
    fun onRedirect(toUrl: String): Boolean {
        val isHttp = toUrl.substringBefore(':').lowercase() == UrlConstants.SCHEME_HTTP
        val upgrade = pending?.takeIf { isHttp && HttpsUpgradePolicy.hostOf(toUrl) == it.host }
        if (upgrade != null) {
            pending = null
            engine.failures.markFailed(upgrade.host)
        }
        return upgrade != null
    }

    /** `onPageFinished` for [url]: if it is the pending attempt's host, the attempt succeeded. */
    fun onPageFinished(url: String?) {
        val upgrade = pending ?: return
        if (url != null && HttpsUpgradePolicy.hostOf(url) == upgrade.host) pending = null
    }

    /** Test hook. */
    fun blockedHosts(): Set<String> = blocked.toSet()

    private fun enterPage(url: String) {
        blocked.clear()
        setTop(url)
        _status.value = statusFor(url)
    }

    private fun follow(url: String) {
        setTop(url)
    }

    private fun setTop(url: String) {
        val host = url.toUri().host?.lowercase()
        topUrl = url
        topHost = host
        val site = host?.let { engine.matchers.matchersOrNull()?.suffixes?.registrableDomain(it) ?: it }
        topSite = site
        protectionOn = site == null || engine.isSiteProtected(site, isIncognito)
    }

    private fun publish(url: String) {
        _status.value = statusFor(url)
    }

    private fun statusFor(url: String): PageProtectionStatus {
        val scheme = url.substringBefore(':').lowercase()
        val isWeb = scheme == UrlConstants.SCHEME_HTTP || scheme == UrlConstants.SCHEME_HTTPS
        if (!isWeb) return PageProtectionStatus.NOT_A_WEB_PAGE
        return PageProtectionStatus(
            blockedTrackerCount = blocked.size,
            protectionOn = protectionOn,
            connectionSecure = scheme == UrlConstants.SCHEME_HTTPS,
            isWebPage = true,
            site = topSite,
        )
    }

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        UrlConstants.BLOCKED_RESPONSE_MIME,
        UrlConstants.BLOCKED_RESPONSE_ENCODING,
        UrlConstants.BLOCKED_RESPONSE_STATUS,
        UrlConstants.BLOCKED_RESPONSE_REASON,
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )
}
