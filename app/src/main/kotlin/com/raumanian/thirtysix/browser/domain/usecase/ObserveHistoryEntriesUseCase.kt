package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Spec 014 — observe the full history list, reverse-chronological. Backed by
 * [HistoryRepository.observeAll]; emits a fresh list on every committed insert/delete.
 */
class ObserveHistoryEntriesUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    operator fun invoke(): Flow<List<HistoryEntry>> = repository.observeAll()
}
