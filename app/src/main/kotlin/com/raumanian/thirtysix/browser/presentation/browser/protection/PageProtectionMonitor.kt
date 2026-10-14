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
import kotlinx.coroutines.flow.update

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
    /** @property started the attempt's page has started (`onPageStarted`); only then can it finish. */
    private data class PendingUpgrade(val host: String, val httpUrl: String, val started: Boolean = false)

    private val blocked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile private var topUrl: String? = null

    @Volatile private var topHost: String? = null

    @Volatile private var topSite: String? = null

    @Volatile private var protectionOn: Boolean = true

    /** A main-frame request was seen and its page has not started yet. */
    @Volatile private var awaitingCommit: Boolean = false

    @Volatile private var pending: PendingUpgrade? = null

    /** The `http://` address to load once the failed attempt's error page has finished. */
    @Volatile private var fallbackDue: String? = null

    private val _status = MutableStateFlow(PageProtectionStatus.NOT_A_WEB_PAGE)
    val status: StateFlow<PageProtectionStatus> = _status.asStateFlow()

    private val blockedHostList = MutableStateFlow<List<String>>(emptyList())

    /**
     * Spec 024 FR-016 — the distinct tracker hosts blocked on this page, in the order they were
     * first blocked. Emits only when a new host joins (IO thread) and when a new page starts;
     * collected only by the privacy dashboard (§V rule 3).
     */
    val blockedHostsFlow: StateFlow<List<String>> = blockedHostList.asStateFlow()

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
        pending?.let { upgrade ->
            if (HttpsUpgradePolicy.hostOf(url) == upgrade.host) pending = upgrade.copy(started = true)
        }
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
        if (blocked.add(host)) {
            _status.value = _status.value.copy(blockedTrackerCount = blocked.size)
            blockedHostList.update { it + host }
        }
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
            fallbackDue = upgrade.httpUrl
        }
        return upgrade?.httpUrl
    }

    /**
     * `onPageFinished`: the `http://` address to load now, if a failed attempt is waiting for its
     * error page to finish. Loading any earlier — even posted from the error callback — can be
     * replaced by that error page's commit (found on a Galaxy A55, 2 of 6 runs).
     */
    fun takeFallbackDue(): String? = fallbackDue.also { fallbackDue = null }

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

    /**
     * `onPageFinished` for [url]: the pending attempt succeeded if its page had started and no
     * error took it first. A load cancelled by a newer one also reports `onPageFinished`, but
     * never started, so it does not end the attempt (found on the 16 KB AVD).
     */
    fun onPageFinished(url: String?) {
        val upgrade = pending ?: return
        if (upgrade.started && url != null && HttpsUpgradePolicy.hostOf(url) == upgrade.host) pending = null
    }

    /** Test hook. */
    fun blockedHosts(): Set<String> = blocked.toSet()

    private fun enterPage(url: String) {
        blocked.clear()
        blockedHostList.value = emptyList()
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
