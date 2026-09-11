package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
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

    companion object {
        const val CALL_CLEAR_SITE_DATA: String = "web.clearCookiesAndSiteData"
        const val CALL_CLEAR_WEB_CACHE: String = "web.clearWebCache"
    }
}
