package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Spec 021 FR-020 / FR-021 — the long-press "Open in new tab" / "Open in incognito tab": a tab
 * opened behind the active one, which stays active (research R9). At the cap the repository's
 * error comes back, for the existing tab-limit message.
 */
class OpenInBackgroundTabUseCase @Inject constructor(
    private val tabs: TabRepository,
    private val incognitoTabs: IncognitoTabRepository,
    private val observeActiveTab: ObserveActiveTabUseCase,
) {
    suspend operator fun invoke(url: String, incognito: Boolean): Result<Tab> {
        val activeAt = observeActiveTab().first()?.lastActiveAt ?: System.currentTimeMillis()
        return if (incognito) {
            incognitoTabs.createBackgroundTab(url, activeAt)
        } else {
            tabs.createBackgroundTab(url, activeAt)
        }
    }
}
