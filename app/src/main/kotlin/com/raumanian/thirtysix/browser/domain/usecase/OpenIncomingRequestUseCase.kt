package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.model.IncomingTarget
import com.raumanian.thirtysix.browser.domain.model.OpenOutcome
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Spec 020 research R6 / contracts C2 — opens content from another app in the browser.
 *
 *  1. A search becomes a URL with the user's chosen engine (Spec 010).
 *  2. The same final URL opened within 2 seconds is a duplicate and does nothing (FR-007).
 *  3. A new **normal** tab is created and becomes active (FR-003). Only the normal tab store is
 *     ever written (`isIncognito = false`), so incoming content never joins that session (FR-017).
 *  4. At the tab cap, the normal tab with the latest `lastActiveAt` is reused (FR-018). If it is
 *     already the active tab, the caller loads the URL into the live page; otherwise its stored
 *     address is set first and the tab is switched to, so the browser's tab-switch load opens
 *     the right page instead of racing it with the old one (research R6).
 *
 * Talks to the normal [TabRepository] directly — the tab use cases it would otherwise take each
 * only delegate to it, and the incognito store is deliberately out of reach.
 *
 * Remembers the last opened URL for the duplicate window, so the caller keeps one instance
 * for as long as it handles requests (the activity-scoped `EntryPointViewModel` does).
 */
class OpenIncomingRequestUseCase @Inject constructor(
    private val buildSearchUrl: BuildSearchUrlUseCase,
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
            is IncomingTarget.Search -> buildSearchUrl(target.text)
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
