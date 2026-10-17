package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabSessionRepository
import javax.inject.Inject

/** Clears visited pages everywhere they are kept: history, and every tab's saved back and forward. */
class ClearBrowsingHistoryUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val tabSessions: TabSessionRepository,
) {
    suspend operator fun invoke() {
        history.clearAll()
        tabSessions.clearAll()
    }
}
