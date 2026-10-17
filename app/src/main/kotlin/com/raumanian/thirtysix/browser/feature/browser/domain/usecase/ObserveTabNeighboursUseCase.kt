package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.TabNeighbours
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.TabOrder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The tabs a swipe on the address bar reaches from the active tab: the same kind, in opening order. */
class ObserveTabNeighboursUseCase @Inject constructor(private val observeAllTabs: ObserveAllTabsUseCase) {
    operator fun invoke(): Flow<TabNeighbours> = observeAllTabs()
        .map { tabs -> TabOrder.neighbours(tabs, tabs.maxByOrNull(Tab::lastActiveAt)?.id) }
        .distinctUntilChanged()
}
