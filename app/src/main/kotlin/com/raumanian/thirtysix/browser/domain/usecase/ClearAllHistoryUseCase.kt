package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Spec 014 FR-022 / FR-024 — wipe every persisted history entry.
 *
 * Destructive and irreversible; the caller MUST have shown the confirmation dialog
 * (`ClearAllHistoryConfirmDialog`) before invoking this. The use case itself performs
 * no gating so it stays trivially testable.
 *
 * @return rows removed — used by the caller for the post-clear announcement and by
 *  tests to assert the wipe actually reached the DAO.
 */
class ClearAllHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(): Int = repository.clearAll()
}
