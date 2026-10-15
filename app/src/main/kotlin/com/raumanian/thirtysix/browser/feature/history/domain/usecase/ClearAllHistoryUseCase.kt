package com.raumanian.thirtysix.browser.feature.history.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Irreversible; the caller shows the confirmation dialog first. No gating here, to keep it testable.
 * @return rows removed, used for the post-clear announcement.
 */
class ClearAllHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(): Int = repository.clearAll()
}
