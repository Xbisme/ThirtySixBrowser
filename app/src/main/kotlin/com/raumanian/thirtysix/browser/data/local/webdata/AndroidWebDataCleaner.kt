package com.raumanian.thirtysix.browser.data.local.webdata

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.domain.privacy.urlHostOrNull
import com.raumanian.thirtysix.browser.domain.repository.WebDataCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Spec 016 — [WebDataCleaner] over the platform web engine (research R5, R6).
 *
 * Total by construction: every platform call runs inside `runCatching`, so a failing or
 * missing web engine becomes a [SiteDataClearOutcome.Failed] or `false`, never a crash
 * (FR-043). Cancellation is the one thing that is rethrown.
 *
 * Logs only which path ran and how long it took — never a URL, a cookie or a stored value
 * (Constitution §I). Quickstart G5 reads these lines to record the path on each device.
 */
@Singleton
class AndroidWebDataCleaner @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val dispatchers: DispatcherProvider,
) : WebDataCleaner {

    override suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome {
        val startedAt = SystemClock.elapsedRealtime()
        val outcome = runCatching {
            // A completion callback that never arrives must not leave the dialog stuck (FR-032).
            withTimeoutOrNull(BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS) { clearSiteData() }
                ?: SiteDataClearOutcome.Failed
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Clearing cookies and site data failed", error)
            SiteDataClearOutcome.Failed
        }
        Log.i(LOG_TAG, "site data: outcome=$outcome elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        return outcome
    }

    override suspend fun clearWebCache(): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val cleared = runCatching {
            withContext(dispatchers.main) {
                // Research R6 — the cache is per application, so a throwaway instance that never
                // attaches to a window or loads a page is enough to empty it for every WebView.
                val webView = WebView(appContext)
                try {
                    webView.clearCache(true)
                } finally {
                    webView.destroy()
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Clearing the web cache failed", error)
        }.isSuccess
        Log.i(LOG_TAG, "web cache: cleared=$cleared elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        return cleared
    }

    override suspend fun storageOrigins(): Set<String> = runCatching {
        withTimeoutOrNull(BrowserLimits.SITE_DATA_DELETE_TIMEOUT_MS) {
            readStorageOrigins().mapNotNullTo(HashSet(), ::urlHostOrNull)
        } ?: emptySet()
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.w(LOG_TAG, "Listing storage origins failed", error)
        emptySet()
    }

    override suspend fun clearExcept(
        keepSites: Set<String>,
        candidateHosts: Set<String>,
        isKept: (host: String) -> Boolean,
    ): SiteDataPath? {
        val startedAt = SystemClock.elapsedRealtime()
        val queryHosts = buildSet {
            keepSites.forEach { site ->
                add(site)
                add(WWW_PREFIX + site)
            }
            candidateHosts.filterTo(this, isKept)
        }
        val path = runCatching {
            val kept = readCookies(queryHosts)
            val siteDataPath = deleteSiteDataExcept(candidateHosts.filterNot(isKept).toSet(), isKept)
            val removed = withTimeoutOrNull(BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS) { removeAllCookies() } != null
            if (removed) {
                writeCookies(kept)
                siteDataPath
            } else {
                null
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Clearing site data except fireproof sites failed", error)
            null
        }
        // Counts only — never a host or a cookie (Constitution §I).
        Log.i(
            LOG_TAG,
            "fire site data: path=$path kept=${keepSites.size} candidates=${candidateHosts.size} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        return path
    }

    /**
     * Research R2 — the kept sites' cookies, keyed by (name, domain, path) so a cookie seen from
     * several query addresses is written back once. With `GET_COOKIE_INFO` each entry is a full
     * `Set-Cookie` string; without it only `name=value` is available (Spec 012 R1 limitation).
     */
    private suspend fun readCookies(hosts: Set<String>): Collection<KeptCookie> = withContext(dispatchers.main) {
        val manager = CookieManager.getInstance()
        val kept = LinkedHashMap<String, KeptCookie>()
        for (host in hosts) {
            for (scheme in SCHEMES) {
                val url = "$scheme://$host/"
                // Lint's RequiresFeature check wants the call directly inside the feature check.
                val entries = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
                    CookieManagerCompat.getCookieInfo(manager, url)
                } else {
                    manager.getCookie(url)?.split(COOKIE_SEPARATOR).orEmpty()
                }
                entries.filter { it.contains('=') }.forEach { entry ->
                    val cookie = KeptCookie.parse(host, entry)
                    kept.putIfAbsent(cookie.key, cookie)
                }
            }
        }
        kept.values
    }

    private suspend fun writeCookies(cookies: Collection<KeptCookie>) {
        if (cookies.isEmpty()) return
        val manager = withContext(dispatchers.main) {
            CookieManager.getInstance().also { manager ->
                cookies.forEach { cookie -> manager.setCookie(cookie.url, cookie.restoreString) }
            }
        }
        withContext(dispatchers.io) { manager.flush() }
    }

    /**
     * Research R3 — per-site deletion where the engine supports it, one site at a time and each
     * bounded; otherwise the framework's per-origin deletion of storage (localStorage and service
     * workers may survive there, FR-003b). Cookies are handled separately by the caller.
     */
    private suspend fun deleteSiteDataExcept(hosts: Set<String>, isKept: (String) -> Boolean): SiteDataPath =
        withContext(dispatchers.main) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                hosts.forEach { host ->
                    withTimeoutOrNull(BrowserLimits.SITE_DATA_DELETE_TIMEOUT_MS) {
                        suspendCancellableCoroutine { continuation ->
                            WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), host) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                }
                SiteDataPath.PerSite
            } else {
                val storage = WebStorage.getInstance()
                readStorageOrigins()
                    .filterNot { origin -> urlHostOrNull(origin)?.let(isKept) == true }
                    .forEach { origin -> storage.deleteOrigin(origin) }
                SiteDataPath.Fallback
            }
        }

    private suspend fun readStorageOrigins(): Set<String> = withContext(dispatchers.main) {
        suspendCancellableCoroutine { continuation ->
            WebStorage.getInstance().getOrigins { origins ->
                val keys = origins?.keys?.mapNotNullTo(HashSet()) { it as? String }.orEmpty()
                if (continuation.isActive) continuation.resume(keys)
            }
        }
    }

    private suspend fun removeAllCookies() = withContext(dispatchers.main) {
        val manager = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            manager.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
        }
    }

    /**
     * Research R5 — the complete path where the installed web engine supports it, otherwise the
     * framework fallback. The feature check and the guarded call share this one function so lint
     * can see the guard.
     */
    private suspend fun clearSiteData(): SiteDataClearOutcome = withContext(dispatchers.main) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            suspendCancellableCoroutine { continuation ->
                WebStorageCompat.deleteBrowsingData(WebStorage.getInstance()) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            SiteDataClearOutcome.Complete
        } else {
            val cookieManager = CookieManager.getInstance()
            suspendCancellableCoroutine { continuation ->
                cookieManager.removeAllCookies {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            // The cookie store writes to disk lazily; flush so the removal survives process death.
            withContext(dispatchers.io) { cookieManager.flush() }
            WebStorage.getInstance().deleteAllData()
            SiteDataClearOutcome.Partial
        }
    }

    private companion object {
        const val LOG_TAG: String = "WebDataCleaner"
        const val WWW_PREFIX: String = "www."
        const val COOKIE_SEPARATOR: String = "; "
        val SCHEMES: List<String> = listOf("https", "http")
    }
}

/**
 * Spec 024 research R2 — one cookie of a fireproof site, read before Fire and written back after.
 *
 * A host-only cookie is reported as `domain=<host>` without a leading dot (G1 spike); writing that
 * attribute back would widen it to every subdomain, so it is dropped and the cookie is set on its
 * own host. A `Secure` cookie is written back through `https`.
 */
internal data class KeptCookie(val key: String, val url: String, val restoreString: String) {
    companion object {
        fun parse(queryHost: String, entry: String): KeptCookie {
            val parts = entry.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            val nameValue = parts.first()
            val attributes = parts.drop(1)
            val domain = attributes.firstOrNull { it.startsWith(DOMAIN_ATTR, ignoreCase = true) }
                ?.substringAfter('=')?.trim()
            val path = attributes.firstOrNull { it.startsWith(PATH_ATTR, ignoreCase = true) }
                ?.substringAfter('=')?.trim() ?: ROOT_PATH
            val secure = attributes.any { it.equals(SECURE_ATTR, ignoreCase = true) }
            val hostOnly = domain != null && !domain.startsWith('.')
            val cookieHost = if (hostOnly) domain.orEmpty() else queryHost
            val kept = if (hostOnly) {
                attributes.filterNot { it.startsWith(DOMAIN_ATTR, ignoreCase = true) }
            } else {
                attributes
            }
            val scheme = if (secure) "https" else "http"
            return KeptCookie(
                key = "${nameValue.substringBefore('=')}|${domain ?: queryHost}|$path",
                url = "$scheme://$cookieHost$path",
                restoreString = (listOf(nameValue) + kept).joinToString("; "),
            )
        }

        private const val DOMAIN_ATTR = "domain="
        private const val PATH_ATTR = "path="
        private const val SECURE_ATTR = "secure"
        private const val ROOT_PATH = "/"
    }
}
