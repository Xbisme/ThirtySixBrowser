package com.raumanian.thirtysix.browser.feature.settings.data.datasource.local

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.urlHostOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A failing or missing web engine becomes a failed outcome, never a crash; only cancellation is
 * rethrown. Logs only the path taken and timings, never a URL, cookie or stored value.
 */
@Singleton
class AndroidWebDataCleaner @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val dispatchers: DispatcherProvider,
    private val cookieManager: Provider<CookieManager>,
) : WebDataCleaner {

    override suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome {
        val startedAt = SystemClock.elapsedRealtime()
        val outcome = runCatching {
            // A completion callback that never arrives must not leave the dialog stuck.
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
                // The cache is per application, so a throwaway WebView empties it for every WebView.
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
            // A timed-out removal must not cost the fireproof cookies, so they go back either way.
            writeCookies(kept)
            if (removed) siteDataPath else null
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(LOG_TAG, "Clearing site data except fireproof sites failed", error)
            null
        }
        // Counts only, never a host or a cookie.
        Log.i(
            LOG_TAG,
            "fire site data: path=$path kept=${keepSites.size} candidates=${candidateHosts.size} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        return path
    }

    // Keyed by (name, domain, path) so a cookie seen from several addresses is written back once.
    // Without GET_COOKIE_INFO only name=value is available, so attributes are lost.
    private suspend fun readCookies(hosts: Set<String>): Collection<KeptCookie> = withContext(dispatchers.main) {
        val manager = cookieManager.get()
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
            cookieManager.get().also { manager ->
                cookies.forEach { cookie -> manager.setCookie(cookie.url, cookie.restoreString) }
            }
        }
        withContext(dispatchers.io) { manager.flush() }
    }

    // Without DELETE_BROWSING_DATA, per-origin deletion may leave localStorage and service workers behind.
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
                val origins = withTimeoutOrNull(BrowserLimits.SITE_DATA_DELETE_TIMEOUT_MS) { readStorageOrigins() }
                origins.orEmpty()
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
        val manager = cookieManager.get()
        suspendCancellableCoroutine { continuation ->
            manager.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
        }
    }

    // The feature check and the guarded call share one function so lint can see the guard.
    private suspend fun clearSiteData(): SiteDataClearOutcome = withContext(dispatchers.main) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            suspendCancellableCoroutine { continuation ->
                WebStorageCompat.deleteBrowsingData(WebStorage.getInstance()) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            SiteDataClearOutcome.Complete
        } else {
            val manager = cookieManager.get()
            suspendCancellableCoroutine { continuation ->
                manager.removeAllCookies {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            // The cookie store writes to disk lazily; flush so the removal survives process death.
            withContext(dispatchers.io) { manager.flush() }
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
 * A fireproof site's cookie, read before Fire and written back after. A host-only cookie is reported
 * as `domain=<host>` without a dot; writing that back would widen it to every subdomain, so it is dropped.
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
