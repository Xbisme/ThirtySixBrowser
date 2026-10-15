package com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingTarget
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.OpenOutcome
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Opens content from another app in a new normal tab; only the normal [TabRepository] is in reach,
 * so incoming content never joins incognito. At the tab cap, the reused tab's address is set before
 * switching so the tab-switch load does not race the old page. Holds the duplicate-window state,
 * so keep one instance per activity.
 */
class OpenIncomingRequestUseCase @Inject constructor(
    private val searchEngine: SearchEngineRepository,
    private val tabs: TabRepository,
    private val observeActiveTab: ObserveActiveTabUseCase,
    private val clock: MonotonicClock,
) {
    private var lastUrl: String? = null
    private var lastOpenedAtMs: Long = 0L

    /** Only [IncomingTarget.Load] and [IncomingTarget.Search] open anything. */
    @Suppress("ReturnCount") // Two guards (nothing to open, duplicate) before the single open path.
    suspend operator fun invoke(target: IncomingTarget): OpenOutcome {
        val url = when (target) {
            is IncomingTarget.Load -> target.url
            is IncomingTarget.Search -> searchEngine.buildSearchUrl(target.text)
            IncomingTarget.Rejected, IncomingTarget.Nothing -> return OpenOutcome.Failed
        }
        val now = clock.nowMs()
        if (url == lastUrl && now - lastOpenedAtMs <= BrowserLimits.INCOMING_DUPLICATE_WINDOW_MS) {
            return OpenOutcome.Duplicate
        }
        lastUrl = url
        lastOpenedAtMs = now
        return when (val created = tabs.createTab(url)) {
            is Result.Success -> OpenOutcome.OpenedInNewTab(created.data.id)
            is Result.Error ->
                if (created.throwable is MaxTabsReachedException) reuseLatestTab(url) else OpenOutcome.Failed
        }
    }

    private suspend fun reuseLatestTab(url: String): OpenOutcome {
        val latest = tabs.observeTabs().first().maxByOrNull { it.lastActiveAt } ?: return OpenOutcome.Failed
        val alreadyActive = observeActiveTab().first()?.id == latest.id
        if (!alreadyActive) {
            tabs.updateTabUrlAndTitle(tabId = latest.id, url = url, title = "")
            tabs.switchActiveTab(latest.id)
        }
        return OpenOutcome.OpenedInExistingTab(latest.id, url, loadInPlace = alreadyActive)
    }
}
