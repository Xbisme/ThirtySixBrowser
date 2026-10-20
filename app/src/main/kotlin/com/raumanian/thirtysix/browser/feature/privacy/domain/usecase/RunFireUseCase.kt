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
import com.raumanian.thirtysix.browser.core.domain.usecase.ClearBrowsingHistoryUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.IsSiteFireproofUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.domain.util.urlHostOrNull
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * One Fire run. The step order is load-bearing (hosts first, incognito before cookies, tabs before
 * anything slow). Each step has its own failure boundary and is idempotent, so an interrupted run
 * can be re-run whole. Bookmarks, downloads, settings and both site lists are never written.
 */
@Suppress("LongParameterList") // One collaborator per kind of data Fire clears or keeps.
class RunFireUseCase @Inject constructor(
    private val fireproofSites: FireproofSiteRepository,
    private val history: HistoryRepository,
    private val clearHistory: ClearBrowsingHistoryUseCase,
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
        val keepSites = fireproofSites.observeAll().first().mapTo(HashSet()) { it.site }
        val isKept: (String) -> Boolean = { host -> IsSiteFireproofUseCase.matches(host, keepSites) }

        // Candidate hosts are read before anything that holds them is cleared.
        val collected = run(FireStep.CollectHosts, failed) { collectHosts() }
        val hosts = collected?.hosts.orEmpty()

        // Incognito ends first: its teardown restores the pre-incognito cookie jar.
        run(FireStep.CloseIncognito, failed) { incognitoTabs.closeAll() }

        // closeAllTabs leaves exactly one fresh Home tab.
        run(FireStep.CloseTabs, failed) {
            screenshots.clearAll()
            tabs.closeAllTabs()
        }

        run(FireStep.History, failed) { clearHistory() }

        // Cookies, site data and the web page cache, keeping fireproof sites.
        val path = clearSiteData(keepSites, hosts, isKept, failed)

        run(FireStep.SitePermissions, failed) { clearPermissions(collected?.permissionOrigins.orEmpty(), isKept) }

        // Site icons, except bookmarked hosts' and fireproof sites'.
        run(FireStep.Favicons, failed) { favicons.retainOnly(iconsToKeep(keepSites, hosts, isKept)) }

        // In-memory session traces, so nothing links visits before and after Fire.
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
            // Never leave a set-aside incognito snapshot that could be written back.
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
        // The web page cache, unless the complete path already emptied it.
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
