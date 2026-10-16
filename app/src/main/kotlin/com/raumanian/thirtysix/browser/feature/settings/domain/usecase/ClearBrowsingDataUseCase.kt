package com.raumanian.thirtysix.browser.feature.settings.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.usecase.ClearBrowsingHistoryUseCase
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Every step runs in its own failure boundary, so an earlier failure never stops later work; a
 * category fails if any of its steps failed. Fireproof sites protect only against Fire, so the
 * cookie step never consults them.
 */
@Suppress("LongParameterList") // each collaborator clears one kind of data
class ClearBrowsingDataUseCase @Inject constructor(
    private val clearHistory: ClearBrowsingHistoryUseCase,
    private val webDataCleaner: WebDataCleaner,
    private val cookieJarSnapshotManager: CookieJarSnapshotManager,
    private val faviconCache: FaviconCache,
    private val screenshotCache: ScreenshotCache,
    private val sitePermissionRepository: SitePermissionRepository,
    private val oneTimeGrants: OneTimeGrantStore,
    private val protectionExceptions: ProtectionExceptionRepository,
    private val incognitoProtectionExceptions: IncognitoProtectionExceptions,
    private val fireproofSites: FireproofSiteRepository,
) {

    /** @param categories non-empty; an empty set is a programming error. */
    suspend operator fun invoke(categories: Set<ClearBrowsingDataCategory>): ClearBrowsingDataResult {
        require(categories.isNotEmpty()) { "At least one category must be selected" }
        val failed = mutableSetOf<ClearBrowsingDataCategory>()
        var siteDataOutcome: SiteDataClearOutcome? = null

        if (ClearBrowsingDataCategory.History in categories) {
            if (!step { clearHistory() }) failed += ClearBrowsingDataCategory.History
        }

        if (ClearBrowsingDataCategory.CookiesAndSiteData in categories) {
            // Discard before the wipe, or closing the last incognito tab could write the old cookies back.
            val discarded = step { cookieJarSnapshotManager.discardSetAsideCookies() }
            val outcome = attempt { webDataCleaner.clearCookiesAndSiteData() } ?: SiteDataClearOutcome.Failed
            siteDataOutcome = outcome
            if (!discarded || outcome == SiteDataClearOutcome.Failed) {
                failed += ClearBrowsingDataCategory.CookiesAndSiteData
            }
        }

        if (ClearBrowsingDataCategory.CachedImagesAndFiles in categories) {
            // A complete site-data clear has already emptied the web cache.
            val webCacheCleared = siteDataOutcome == SiteDataClearOutcome.Complete ||
                attempt { webDataCleaner.clearWebCache() } == true
            val iconsCleared = step { faviconCache.clearAll() }
            val previewsCleared = step { screenshotCache.clearAll() }
            if (!(webCacheCleared && iconsCleared && previewsCleared)) {
                failed += ClearBrowsingDataCategory.CachedImagesAndFiles
            }
        }

        failed += clearPerSiteSettings(categories)
        return ClearBrowsingDataResult(requested = categories, failed = failed)
    }

    // Each saved per-site list is cleared together with its in-memory counterpart.
    private suspend fun clearPerSiteSettings(
        categories: Set<ClearBrowsingDataCategory>,
    ): Set<ClearBrowsingDataCategory> {
        val failed = mutableSetOf<ClearBrowsingDataCategory>()
        if (ClearBrowsingDataCategory.SitePermissions in categories) {
            val stored = attempt { sitePermissionRepository.clearAll() } is Result.Success
            oneTimeGrants.clear()
            if (!stored) failed += ClearBrowsingDataCategory.SitePermissions
        }
        if (ClearBrowsingDataCategory.SitesWithoutProtection in categories) {
            val stored = attempt { protectionExceptions.clearAll() } is Result.Success
            incognitoProtectionExceptions.clear()
            if (!stored) failed += ClearBrowsingDataCategory.SitesWithoutProtection
        }
        if (ClearBrowsingDataCategory.FireproofSites in categories) {
            val stored = attempt { fireproofSites.clearAll() } is Result.Success
            if (!stored) failed += ClearBrowsingDataCategory.FireproofSites
        }
        return failed
    }

    private suspend fun step(block: suspend () -> Unit): Boolean = attempt(block) != null

    // Cancellation is rethrown, never mistaken for a failed step.
    private suspend fun <T : Any> attempt(block: suspend () -> T): T? =
        runCatching { block() }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrNull()
}
