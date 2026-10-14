package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Spec 016 FR-025 – FR-033 — clear the selected kinds of browsing data.
 *
 * Coordinates several repositories and platform seams, which is exactly the use-case layer's
 * job under Constitution §IV — no repository gains a dependency on another.
 *
 * For each category present, in this fixed order:
 *  - **History** → `HistoryRepository.clearAll()`.
 *  - **Cookies and site data** → `discardSetAsideCookies()` **first** (research R7), then
 *    `WebDataCleaner.clearCookiesAndSiteData()`. `Complete` and `Partial` count as cleared —
 *    `Partial` is the documented older-engine limitation (spec A17) — and only `Failed` fails.
 *  - **Cached images and files** → `WebDataCleaner.clearWebCache()`, skipped when this same call
 *    already produced `Complete` because the engine emptied the cache then (spec A16); then
 *    `FaviconCache.clearAll()` and `ScreenshotCache.clearAll()`, always.
 *  - **Site permissions** (Spec 021 FR-010a) → `SitePermissionRepository.clearAll()` and the
 *    in-memory one-time grants.
 *  - **Sites without protection** (Spec 023 FR-016a) → `ProtectionExceptionRepository.clearAll()`
 *    and the incognito choices held in memory.
 *  - **Fireproof sites** (Spec 024 FR-013) → `FireproofSiteRepository.clearAll()`. Fireproofing
 *    protects only against Fire: the cookie step above never consults it (INV-10).
 *
 * Every category, and every step inside a category, runs in its own failure boundary: an
 * earlier failure never stops later work (FR-033, FR-043), and a category is failed if any of
 * its steps threw, returned `false` or returned `Failed`. Nothing outside the categories
 * is touched — no tabs, bookmarks, downloads or settings (FR-031). Returns normally in every
 * case except cancellation, which is rethrown.
 */
@Suppress("LongParameterList") // Specs 021 and 023 add collaborators; each clears one kind of data.
class ClearBrowsingDataUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
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

    /** @param categories non-empty — an empty set is a programming error (FR-026). */
    suspend operator fun invoke(categories: Set<ClearBrowsingDataCategory>): ClearBrowsingDataResult {
        require(categories.isNotEmpty()) { "At least one category must be selected" }
        val failed = mutableSetOf<ClearBrowsingDataCategory>()
        var siteDataOutcome: SiteDataClearOutcome? = null

        if (ClearBrowsingDataCategory.History in categories) {
            if (!step { historyRepository.clearAll() }) failed += ClearBrowsingDataCategory.History
        }

        if (ClearBrowsingDataCategory.CookiesAndSiteData in categories) {
            // R7 — discard BEFORE the wipe. The reverse order leaves a window in which closing the
            // last incognito tab writes the old cookies back after the user cleared them.
            val discarded = step { cookieJarSnapshotManager.discardSetAsideCookies() }
            val outcome = attempt { webDataCleaner.clearCookiesAndSiteData() } ?: SiteDataClearOutcome.Failed
            siteDataOutcome = outcome
            if (!discarded || outcome == SiteDataClearOutcome.Failed) {
                failed += ClearBrowsingDataCategory.CookiesAndSiteData
            }
        }

        if (ClearBrowsingDataCategory.CachedImagesAndFiles in categories) {
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

    /**
     * The two opt-in per-site categories (Spec 021 FR-010a, Spec 023 FR-016a): the saved list
     * and its in-memory counterpart are both cleared. Returns the categories that failed.
     */
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

    /** Runs one step in its own failure boundary; `true` when it returned normally. */
    private suspend fun step(block: suspend () -> Unit): Boolean = attempt(block) != null

    /**
     * Runs [block] and returns its value, or `null` if it threw. Cancellation is rethrown, never
     * mistaken for a failed step.
     */
    private suspend fun <T : Any> attempt(block: suspend () -> T): T? =
        runCatching { block() }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrNull()
}
