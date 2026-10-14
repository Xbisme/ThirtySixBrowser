package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 021 FR-020 — the long-press notice's "Switch": makes [tabId] active, normal or incognito
 * (incognito tabs have negative ids, Spec 012 R3).
 */
class SwitchToTabUseCase @Inject constructor(
    private val tabs: TabRepository,
    private val incognitoTabs: IncognitoTabRepository,
) {
    suspend operator fun invoke(tabId: Long) =
        if (tabId < 0L) incognitoTabs.switchActiveTab(tabId) else tabs.switchActiveTab(tabId)
}
