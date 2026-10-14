package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The persisted tabs, most recently active first. Never empty once emitting:
 * [TabRepository] seeds a tab before the first emission.
 */
class ObserveTabsUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    operator fun invoke(): Flow<List<Tab>> = repository.observeTabs()
}
