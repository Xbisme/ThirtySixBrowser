package com.raumanian.thirtysix.browser.core.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Whether the active tab (the head of [ObserveAllTabsUseCase]) is incognito. */
class ObserveActiveTabIsIncognitoUseCase @Inject constructor(
    private val observeAllTabs: ObserveAllTabsUseCase,
) {
    operator fun invoke(): Flow<Boolean> = observeAllTabs()
        .map { tabs -> tabs.firstOrNull()?.isIncognito ?: false }
        .distinctUntilChanged()
}
