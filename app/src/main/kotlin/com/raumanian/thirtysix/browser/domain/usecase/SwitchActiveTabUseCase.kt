package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — make [tabId] the active tab (FR-013 / R1).
 *
 * Implementation writes `lastActiveAt = now()` on the leaving tab BEFORE the
 * new active tab so ordering remains stable. The active-tab pointer is
 * derived as `MAX(last_active_at)` per R1.
 *
 * No-op if [tabId] does not exist (already-closed tab race).
 */
class SwitchActiveTabUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    suspend operator fun invoke(tabId: Long) = repository.switchActiveTab(tabId)
}
