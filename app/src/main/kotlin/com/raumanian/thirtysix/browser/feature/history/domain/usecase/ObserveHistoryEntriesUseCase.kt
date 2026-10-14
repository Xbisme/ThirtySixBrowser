package com.raumanian.thirtysix.browser.feature.history.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The full history, newest first; emits on every committed insert or delete. */
class ObserveHistoryEntriesUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    operator fun invoke(): Flow<List<HistoryEntry>> = repository.observeAll()
}
