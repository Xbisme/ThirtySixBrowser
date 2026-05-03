package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.Tab
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Spec 011 — derives the active [Tab] from [ObserveTabsUseCase] as the row
 * with the maximum `lastActiveAt` (R1 — active-tab pointer derivation).
 *
 * Returns `null` only during the initial moment between Flow subscription
 * and the first emission; once the auto-create-on-empty rule (FR-019 / R5)
 * has fired, the Flow always carries a non-null active tab.
 *
 * Consumers (`BrowserViewModel.init` for `currentUrl` seeding,
 * `TabsViewModel` for the active-tab visual indicator) read this directly.
 */
class ObserveActiveTabUseCase @Inject constructor(
    private val observeTabs: ObserveTabsUseCase,
) {
    operator fun invoke(): Flow<Tab?> =
        observeTabs().map { tabs -> tabs.maxByOrNull(Tab::lastActiveAt) }
}
