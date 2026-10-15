package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The active tab, normal or incognito: the one with the latest `lastActiveAt`.
 * Null only before the repository has seeded its first tab.
 */
class ObserveActiveTabUseCase @Inject constructor(
    private val observeAllTabs: ObserveAllTabsUseCase,
) {
    operator fun invoke(): Flow<Tab?> =
        observeAllTabs().map { tabs -> tabs.maxByOrNull(Tab::lastActiveAt) }
}
