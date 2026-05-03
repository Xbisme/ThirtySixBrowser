package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.Tab
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Spec 011 — derives the active [Tab] as the row with the maximum
 * `lastActiveAt` (R1 — active-tab pointer derivation).
 *
 * Spec 012 update: source switched from [ObserveTabsUseCase] (normal tabs
 * only) to [ObserveAllTabsUseCase] (merged normal + incognito flow) so the
 * active-tab pointer correctly tracks an incognito tab when one is the
 * most-recently-active. The merged Flow's first element is already the
 * active tab per the merged sort (data-model.md merged-ordering), so this
 * use case can simply return its head — but we keep the explicit
 * `maxByOrNull` form for defensive symmetry with the Spec 011 contract.
 *
 * Returns `null` only during the initial moment between Flow subscription
 * and the first emission; once the auto-create-on-empty rule (FR-019 / R5)
 * has fired, the Flow always carries a non-null active tab.
 *
 * Consumers (`BrowserViewModel.init` for `currentUrl` seeding,
 * `TabsViewModel` for the active-tab visual indicator) read this directly.
 */
class ObserveActiveTabUseCase @Inject constructor(
    private val observeAllTabs: ObserveAllTabsUseCase,
) {
    operator fun invoke(): Flow<Tab?> =
        observeAllTabs().map { tabs -> tabs.maxByOrNull(Tab::lastActiveAt) }
}
