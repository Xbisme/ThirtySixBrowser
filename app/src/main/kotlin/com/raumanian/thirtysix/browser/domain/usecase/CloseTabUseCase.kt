package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — close [tabId] (US3 / FR-024).
 *
 * Durable: once this returns, the row is gone from `TabDao`. If the post-
 * delete count would drop to 0, a fresh home tab is auto-created in the
 * same coroutine (FR-019 / R5 / US3 #3). The new active tab is the
 * most-recently-active surviving tab (US3 #2 / R1).
 */
class CloseTabUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    suspend operator fun invoke(tabId: Long) = repository.closeTab(tabId)
}
