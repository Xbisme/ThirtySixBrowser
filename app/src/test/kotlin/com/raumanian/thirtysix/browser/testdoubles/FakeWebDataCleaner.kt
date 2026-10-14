package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.domain.repository.WebDataCleaner
import kotlinx.coroutines.CompletableDeferred

/**
 * Spec 016 T068 — in-memory [WebDataCleaner].
 *
 * Each call appends to [callLog], which a test can share with other fakes to assert ordering.
 * The outcome, the boolean result, or a thrown error are configurable per step. [siteDataGate],
 * when set, holds the site-data step open until the test completes it — how a test keeps a clear
 * "in progress" long enough to prove the dialog cannot be re-submitted or dismissed.
 */
class FakeWebDataCleaner(
    private val callLog: MutableList<String> = mutableListOf(),
    var siteDataOutcome: SiteDataClearOutcome = SiteDataClearOutcome.Complete,
    var webCacheResult: Boolean = true,
) : WebDataCleaner {

    var siteDataError: Throwable? = null
    var webCacheError: Throwable? = null
    var siteDataGate: CompletableDeferred<Unit>? = null

    override suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome {
        callLog += CALL_CLEAR_SITE_DATA
        siteDataGate?.await()
        siteDataError?.let { throw it }
        return siteDataOutcome
    }

    override suspend fun clearWebCache(): Boolean {
        callLog += CALL_CLEAR_WEB_CACHE
        webCacheError?.let { throw it }
        return webCacheResult
    }

    /** Spec 024 — hosts [storageOrigins] reports. */
    var origins: Set<String> = emptySet()

    /** Spec 024 — what [clearExcept] returns; `null` means the cookie wipe failed. */
    var clearExceptPath: SiteDataPath? = SiteDataPath.PerSite

    /** Spec 024 — the arguments of the last [clearExcept] call (keepSites, candidateHosts). */
    var lastClearExcept: Pair<Set<String>, Set<String>>? = null

    override suspend fun storageOrigins(): Set<String> {
        callLog += CALL_STORAGE_ORIGINS
        return origins
    }

    override suspend fun clearExcept(
        keepSites: Set<String>,
        candidateHosts: Set<String>,
        isKept: (host: String) -> Boolean,
    ): SiteDataPath? {
        callLog += CALL_CLEAR_EXCEPT
        lastClearExcept = keepSites to candidateHosts
        siteDataError?.let { throw it }
        return clearExceptPath
    }

    companion object {
        const val CALL_CLEAR_SITE_DATA: String = "web.clearCookiesAndSiteData"
        const val CALL_CLEAR_WEB_CACHE: String = "web.clearWebCache"
        const val CALL_STORAGE_ORIGINS: String = "web.storageOrigins"
        const val CALL_CLEAR_EXCEPT: String = "web.clearExcept"
    }
}
