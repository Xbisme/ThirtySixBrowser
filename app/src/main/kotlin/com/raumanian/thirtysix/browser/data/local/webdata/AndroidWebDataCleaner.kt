package com.raumanian.thirtysix.browser.data.local.webdata

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
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
    }
}
