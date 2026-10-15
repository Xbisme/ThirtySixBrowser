package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject

/** Makes [tabId] the active tab; a no-op if it was already closed. */
class SwitchActiveTabUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    suspend operator fun invoke(tabId: Long) = repository.switchActiveTab(tabId)
}
