package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
/**
 * Spec 016 — clears data held by the web engine (FR-028, FR-029, FR-042, FR-043).
 *
 * Only the implementation touches `android.webkit` and `androidx.webkit`; the use case that
 * consumes this seam, and every test of it, stays on the plain JVM.
 *
 * Every member is total: failure is expressed in the return value, never thrown.
 * `CancellationException` is not caught.
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

    /**
     * Spec 024 data-model step 1 — hosts of the origins the web engine reports as holding storage
     * (`WebStorage.getOrigins`). Empty on any failure; never throws.
     */
    suspend fun storageOrigins(): Set<String>

    /**
     * Spec 024 FR-003, FR-010 — clears cookies and site data of every site except [keepSites]
     * (registrable domains), using [candidateHosts] (data-model step 1) to find the sites that hold
     * data and [isKept] to tell them apart (research R2, R3):
     *  1. reads the kept sites' cookies, with their attributes where the engine can report them;
     *  2. deletes each non-kept candidate's site data — per site where the engine supports it
     *     ([SiteDataPath.PerSite]), otherwise by origin ([SiteDataPath.Fallback]);
     *  3. removes every cookie, then writes the kept ones back.
     *
     * Callers MUST have emptied the incognito cookie set-aside first. Returns `null` only when the
     * cookies could not be removed; never throws except for cancellation.
     */
    suspend fun clearExcept(
        keepSites: Set<String>,
        candidateHosts: Set<String>,
        isKept: (host: String) -> Boolean,
    ): SiteDataPath?
}
