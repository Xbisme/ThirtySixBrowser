// Spec 016 contract — platform seam for clearing web data (FR-028, FR-029, FR-042, FR-043).
//
// Interface target:       app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/WebDataCleaner.kt
// Outcome type target:    app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SiteDataClearOutcome.kt
// Implementation target:  app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/webdata/AndroidWebDataCleaner.kt
//                         (@Singleton; bound with @Binds in a module under di/)
//
// Only the implementation touches android.webkit and androidx.webkit. The use case that consumes
// this seam, and every test of it, stays on the plain JVM.

package com.raumanian.thirtysix.browser.domain.repository

/** What the site-data step achieved. See research R5 and spec A16 / A17. */
enum class SiteDataClearOutcome {
    /**
     * The web engine removed cookies and every kind of site data — including service workers and
     * Cache Storage — in one operation. That operation also emptied the web page cache (spec A16).
     */
    Complete,

    /**
     * The web engine predates complete removal. Cookies and web storage (local and session
     * storage, IndexedDB, file-system storage) were removed; service workers and Cache Storage
     * were not, and the web page cache was not touched (spec A17). Reported as cleared, not failed.
     */
    Partial,

    /** A required platform call threw or never completed. The category is reported as failed. */
    Failed,
}

/**
 * Clears data held by the web engine. Every member is total: failure is expressed in the return
 * value, never thrown. `CancellationException` is not caught.
 */
interface WebDataCleaner {

    /**
     * Removes all cookies and all site data (FR-028).
     *
     * Mechanism (research R5):
     *  - When `WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)` is true:
     *    `WebStorageCompat.deleteBrowsingData(WebStorage.getInstance(), callback)` on the main
     *    thread, suspending until its callback runs → [SiteDataClearOutcome.Complete].
     *  - Otherwise: `CookieManager.removeAllCookies(callback)` on the main thread, suspending until
     *    its callback runs, then `CookieManager.flush()` off the main thread; and
     *    `WebStorage.getInstance().deleteAllData()` → [SiteDataClearOutcome.Partial].
     *  - Any required call throwing → [SiteDataClearOutcome.Failed].
     *
     * Callers MUST have emptied the incognito cookie set-aside before calling this (research R7).
     */
    suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome

    /**
     * Empties the web page cache (FR-029).
     *
     * Mechanism (research R6): construct a `WebView` on the main thread, call `clearCache(true)` —
     * which clears the cache for every WebView in the app — and `destroy()` it. The engine performs
     * the removal asynchronously and reports no completion, so success means the calls returned
     * without throwing.
     *
     * Callers skip this when [clearCookiesAndSiteData] returned [SiteDataClearOutcome.Complete] in
     * the same clear, because that operation already emptied the cache (spec A16).
     *
     * @return `true` if the calls completed without throwing; `false` otherwise.
     */
    suspend fun clearWebCache(): Boolean
}
