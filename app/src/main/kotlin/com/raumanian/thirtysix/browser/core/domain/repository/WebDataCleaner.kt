package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
/** Clears web-engine data. Total: failure shows in the return value; cancellation is not caught. */
interface WebDataCleaner {

    /**
     * Complete where the engine supports `DELETE_BROWSING_DATA`, otherwise Partial (cookies and
     * web storage only). Call only after emptying the incognito cookie set-aside.
     */
    suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome

    /**
     * Clears the cache of every WebView via a throwaway WebView; the engine reports no completion.
     * Skipped after a Complete site-data clear, which already emptied the cache.
     */
    suspend fun clearWebCache(): Boolean

    /** Hosts of origins the engine reports as holding storage; empty on failure. */
    suspend fun storageOrigins(): Set<String>

    /**
     * Snapshots the kept sites' cookies, deletes the others' site data (per site where supported),
     * wipes cookies and writes the kept ones back. Call after emptying the incognito set-aside.
     * Null only when cookies could not be removed.
     */
    suspend fun clearExcept(
        keepSites: Set<String>,
        candidateHosts: Set<String>,
        isKept: (host: String) -> Boolean,
    ): SiteDataPath?
}
