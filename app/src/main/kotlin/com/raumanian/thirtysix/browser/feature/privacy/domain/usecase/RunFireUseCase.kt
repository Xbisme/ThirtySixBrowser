package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireResult
import com.raumanian.thirtysix.browser.core.domain.model.FireStep
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.usecase.IsSiteFireproofUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.domain.util.urlHostOrNull
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Spec 024 FR-003 – FR-008 — one Fire run: data-model "Fire run" steps 1–10, in order.
 *
 * Coordinates repositories and platform seams, which is the use-case layer's job under
 * Constitution §IV, exactly like [ClearBrowsingDataUseCase]. The order is load-bearing:
 *  - candidate hosts are collected before anything that holds them is cleared (INV-3);
 *  - incognito ends before any cookie is read, so the live jar is back to its pre-incognito state
 *    and no incognito cookie can be kept for a fireproof site (INV-2, FR-011);
 *  - tabs go before anything slow, so an interrupted run never brings an old tab back (FR-007).
 *
 * Every step runs in its own failure boundary: a failed step is reported in [FireResult] and the
 * next step still runs. Every step is idempotent, so the coordinator can re-run a whole interrupted
 * run on the next start. Bookmarks, downloads, settings, sites without protection and the
 * fireproof list are never written (INV-4). Cancellation is rethrown.
 */
@Suppress("LongParameterList") // One collaborator per kind of data Fire clears or keeps.
class RunFireUseCase @Inject constructor(
    private val fireproofSites: FireproofSiteRepository,
    private val history: HistoryRepository,
    private val tabs: TabRepository,
    private val incognitoTabs: IncognitoTabRepository,
    private val sitePermissions: SitePermissionRepository,
    private val oneTimeGrants: OneTimeGrantStore,
    private val webDataCleaner: WebDataCleaner,
    private val cookieJar: CookieJarSnapshotManager,
    private val favicons: FaviconCache,
    private val screenshots: ScreenshotCache,
    private val bookmarks: BookmarkRepository,
    private val httpsFailures: HttpsUpgradeFailures,
    private val scriptSession: PrivacyScriptSession,
    private val stepObserver: FireStepObserver,
    private val dispatchers: DispatcherProvider,
) {

    suspend operator fun invoke(): FireResult {
        val failed = mutableSetOf<FireStep>()
        val keepSites = fireproofSites.currentSites()
        val isKept: (String) -> Boolean = { host -> IsSiteFireproofUseCase.matches(host, keepSites) }

        // 1 — candidate hosts (INV-3). Read before anything that holds them is cleared.
        val collected = run(FireStep.CollectHosts, failed) { collectHosts() }
        val hosts = collected?.hosts.orEmpty()

        // 2 — incognito ends first: its teardown restores the pre-incognito cookie jar (INV-2).
        run(FireStep.CloseIncognito, failed) { incognitoTabs.closeAll() }

        // 3 (+10) — every normal tab goes; closeAllTabs leaves exactly one fresh Home tab (Spec 011).
        run(FireStep.CloseTabs, failed) {
            screenshots.clearAll()
            tabs.closeAllTabs()
        }

        // 4 — history, in full (spec Assumptions).
        run(FireStep.History, failed) { history.clearAll() }

        // 5, 6 — cookies, site data and the web page cache, keeping fireproof sites (research R2–R4).
        val path = clearSiteData(keepSites, hosts, isKept, failed)

        // 7 — remembered and one-time site permissions, except fireproof sites' (FR-004a).
        run(FireStep.SitePermissions, failed) { clearPermissions(collected?.permissionOrigins.orEmpty(), isKept) }

        // 8 — site icons, except bookmarked hosts' and fireproof sites' (FR-008).
        run(FireStep.Favicons, failed) { favicons.retainOnly(iconsToKeep(keepSites, hosts, isKept)) }

        // 9 — in-memory session traces, so nothing links visits before and after Fire (FR-003a).
        run(FireStep.SessionTraces, failed) {
            httpsFailures.clear()
            scriptSession.regenerate()
        }

        return FireResult(failedSteps = failed, siteDataPath = path)
    }

    private class Collected(val hosts: Set<String>, val permissionOrigins: List<String>)

    private suspend fun collectHosts(): Collected {
        val permissions = sitePermissions.observeAll().first().map { it.origin }.distinct()
        val hosts = buildSet {
            addAll(history.distinctHosts())
            tabs.observeTabs().first().mapNotNullTo(this) { urlHostOrNull(it.url) }
            incognitoTabs.observeTabs().first().mapNotNullTo(this) { urlHostOrNull(it.url) }
            permissions.mapNotNullTo(this, ::urlHostOrNull)
            addAll(webDataCleaner.storageOrigins())
        }
        return Collected(hosts, permissions)
    }

    private suspend fun clearSiteData(
        keepSites: Set<String>,
        hosts: Set<String>,
        isKept: (String) -> Boolean,
        failed: MutableSet<FireStep>,
    ): SiteDataPath? {
        val path = run(FireStep.CookiesAndSiteData, failed) {
            // Spec 016 R7 — never leave a set-aside incognito snapshot that could be written back.
            cookieJar.discardSetAsideCookies()
            if (keepSites.isEmpty()) {
                when (webDataCleaner.clearCookiesAndSiteData()) {
                    SiteDataClearOutcome.Complete -> SiteDataPath.Complete
                    SiteDataClearOutcome.Partial -> SiteDataPath.Fallback
                    SiteDataClearOutcome.Failed -> null
                }
            } else {
                webDataCleaner.clearExcept(keepSites, hosts, isKept)
            }
        }
        if (path == null) failed += FireStep.CookiesAndSiteData
        // The web page cache, unless the complete path already emptied it (Spec 016 A16).
        if (path != SiteDataPath.Complete) {
            val cleared = run(FireStep.WebCache, failed) { webDataCleaner.clearWebCache() } == true
            if (!cleared) failed += FireStep.WebCache
        }
        return path
    }

    private suspend fun clearPermissions(origins: List<String>, isKept: (String) -> Boolean) {
        val keep: (String) -> Boolean = { origin -> urlHostOrNull(origin)?.let(isKept) == true }
        origins.filterNot(keep).forEach { origin -> sitePermissions.removeSite(origin) }
        withContext(dispatchers.main) { oneTimeGrants.retainOnly(keep) }
    }

    private suspend fun iconsToKeep(keepSites: Set<String>, hosts: Set<String>, isKept: (String) -> Boolean) =
        buildSet {
            bookmarks.getAllBookmarks().mapNotNullTo(this) { urlHostOrNull(it.url) }
            hosts.filterTo(this, isKept)
            keepSites.forEach { site ->
                add(site)
                add(WWW_PREFIX + site)
            }
        }

    /** One step in its own failure boundary; `null` (and [step] in [failed]) when it threw. */
    private suspend fun <T> run(step: FireStep, failed: MutableSet<FireStep>, block: suspend () -> T): T? =
        runCatching {
            stepObserver.beforeStep(step)
            block()
        }.onFailure { error ->
            if (error is CancellationException) throw error
            failed += step
        }.getOrNull()

    private companion object {
        const val WWW_PREFIX = "www."
    }
}
