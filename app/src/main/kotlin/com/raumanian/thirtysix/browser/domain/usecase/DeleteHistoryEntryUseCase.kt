package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Spec 014 FR-020 — hard-delete a single history entry by id.
 *
 * Deletion is immediate and unconfirmed by design: a single row is cheap to re-create
 * by re-visiting the page, so the spec reserves the confirmation dialog for the
 * destructive clear-all path ([ClearAllHistoryUseCase] / FR-024).
 *
 * @return rows removed — `1` when the entry existed, `0` when it had already been
 *  removed (for instance by a concurrent clear-all).
 */
class DeleteHistoryEntryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Long): Int = repository.deleteById(id)
}
