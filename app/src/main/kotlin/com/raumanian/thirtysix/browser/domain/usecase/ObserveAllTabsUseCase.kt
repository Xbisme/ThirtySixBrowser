package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Spec 012 — merge the persistent normal-tab Flow with the in-memory
 * incognito-tab Flow into a single ordered list.
 *
 * Sort key (data-model.md merged-ordering):
 *  1. `lastActiveAt DESC` (most recently active first — drives the active
 *     tab pointer + switcher ordering)
 *  2. `isIncognito ASC` (when timestamps tie, normal tabs precede incognito —
 *     deterministic tiebreaker)
 *  3. `id ASC` (final tiebreaker — guarantees stable order in tests)
 *
 * The first element of the emitted list is the active tab. After the close
 * of the last incognito tab, the merged head naturally falls back to the
 * most-recently-active normal tab — no special-case logic in the consumer
 * (research.md R9).
 */
class ObserveAllTabsUseCase @Inject constructor(
    private val tabRepository: TabRepository,
    private val incognitoRepository: IncognitoTabRepository,
) {
    operator fun invoke(): Flow<List<Tab>> = combine(
        tabRepository.observeTabs(),
        incognitoRepository.observeTabs(),
    ) { normal, incognito ->
        (normal + incognito).sortedWith(
            compareByDescending(Tab::lastActiveAt)
                .thenBy { tab -> if (tab.isIncognito) 1 else 0 }
                .thenBy(Tab::id),
        )
    }
}
