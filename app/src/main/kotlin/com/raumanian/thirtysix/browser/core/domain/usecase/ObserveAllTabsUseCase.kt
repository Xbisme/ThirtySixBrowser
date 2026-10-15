package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Normal and incognito tabs in one list whose head is the active tab: most recently active first,
 * ties broken by normal before incognito, then by id. Closing the last incognito tab needs no
 * special case: the head simply falls back to the most recent normal tab.
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
