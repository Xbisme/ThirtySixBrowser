package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.domain.protection.HttpsUpgradePolicy
import com.raumanian.thirtysix.browser.feature.browser.domain.protection.NavigationPreparer
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.NetworkTimeouts
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The protections' state for one WebView: the page on screen, its blocked trackers and a pending
 * HTTPS upgrade. [onMainFrameRequest] and [onSubresource] run on WebView IO threads, the rest on
 * the main thread; nothing here touches the database.
 */
@Suppress("TooManyFunctions") // One entry point per WebView callback it follows.
class PageProtectionMonitor internal constructor(
    private val engine: ProtectionEngine,
    val isIncognito: Boolean,
) {
    private data class PendingUpgrade(val host: String, val httpUrl: String, val started: Boolean = false)

    private val blocked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile private var topUrl: String? = null

    @Volatile private var topHost: String? = null

    @Volatile private var topSite: String? = null

    @Volatile private var protectionOn: Boolean = true

    @Volatile private var awaitingCommit: Boolean = false

    @Volatile private var pending: PendingUpgrade? = null

    @Volatile private var fallbackDue: String? = null

    private val _status = MutableStateFlow(PageProtectionStatus.NOT_A_WEB_PAGE)
    val status: StateFlow<PageProtectionStatus> = _status.asStateFlow()

    private val blockedHostList = MutableStateFlow<List<String>>(emptyList())

    /** Distinct tracker hosts blocked on this page, in order; collected only by the dashboard. */
    val blockedHostsFlow: StateFlow<List<String>> = blockedHostList.asStateFlow()

    val currentSite: String? get() = topSite

    /** IO thread: a new load (or reload) starts; the per-page count resets before any resource. */
    fun onMainFrameRequest(url: String) {
        engine.matchers.awaitMatchers(NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS)
        awaitingCommit = true
        enterPage(url)
    }

    /** Main thread. After redirects the page keeps its count but follows the committed site. */
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

    /** When the pending HTTPS attempt failed: marks the host failed and returns its `http://` address. */
    fun onMainFrameFailure(failedUrl: String?): String? {
        val upgrade = pending?.takeIf { failedUrl != null && isSecureAttempt(it, failedUrl) }
        if (upgrade != null) {
            pending = null
            engine.failures.markFailed(upgrade.host)
            fallbackDue = upgrade.httpUrl
        }
        return upgrade?.httpUrl
    }

    /** The `http://` address to load once a failed attempt's error page has finished, or `null`. */
    fun takeFallbackDue(): String? = fallbackDue.also { fallbackDue = null }

    private fun isSecureAttempt(upgrade: PendingUpgrade, url: String): Boolean =
        url.startsWith(UrlConstants.HTTPS_SCHEME_PREFIX, ignoreCase = true) &&
            HttpsUpgradePolicy.hostOf(url) == upgrade.host

    /** A redirect: `true` when the pending HTTPS attempt was sent back to `http://`. */
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
     * The pending attempt succeeds only if its page had started: a load cancelled by a newer one
     * also reports `onPageFinished` without ever starting.
     */
    fun onPageFinished(url: String?) {
        val upgrade = pending ?: return
        if (upgrade.started && url != null && HttpsUpgradePolicy.hostOf(url) == upgrade.host) pending = null
    }

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
