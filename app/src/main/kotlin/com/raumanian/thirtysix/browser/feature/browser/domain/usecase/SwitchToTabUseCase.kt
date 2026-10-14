package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject

/** The long-press notice's "Switch": makes [tabId] active, normal or incognito (negative id). */
class SwitchToTabUseCase @Inject constructor(
    private val tabs: TabRepository,
    private val incognitoTabs: IncognitoTabRepository,
) {
    suspend operator fun invoke(tabId: Long) =
        if (tabId < 0L) incognitoTabs.switchActiveTab(tabId) else tabs.switchActiveTab(tabId)
}
