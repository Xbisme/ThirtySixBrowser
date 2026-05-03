package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Spec 011 — emits the current persisted tab list, ordered by `lastActiveAt`
 * DESC then `id` ASC (FR-014).
 *
 * Always emits at least one tab — empty-state seeding (FR-019 / R5) is
 * guaranteed by [TabRepository] before the first emission. Consumers
 * (`TabsViewModel`, `BrowserViewModel.tabCount` derivation) can rely on
 * `firstOrNull()` always returning a non-null `Tab` after the first
 * collection round.
 */
class ObserveTabsUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    operator fun invoke(): Flow<List<Tab>> = repository.observeTabs()
}
