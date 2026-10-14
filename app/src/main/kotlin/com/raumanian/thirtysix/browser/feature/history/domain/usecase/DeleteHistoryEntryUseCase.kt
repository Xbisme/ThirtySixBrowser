package com.raumanian.thirtysix.browser.feature.history.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Unconfirmed by design: a single row is cheap to re-create, so only clear-all asks first.
 * @return rows removed; `0` when the entry was already gone, e.g. by a concurrent clear-all.
 */
class DeleteHistoryEntryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Long): Int = repository.deleteById(id)
}
