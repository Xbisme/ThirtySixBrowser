package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import javax.inject.Inject

/**
 * Records a successful page load; a no-op in incognito. Call it only when a page finishes, never
 * for a failed load. Returns the row id, or [SUPPRESSED], so a late page title can be patched on.
 */
class RecordHistoryEntryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(url: String, title: String, isIncognito: Boolean): Long {
        // The Home address is never loaded, so it should not arrive here; this keeps it out anyway.
        if (isIncognito || url == UrlConstants.HOME_PAGE_URL) return SUPPRESSED
        return repository.recordVisit(url, title, System.currentTimeMillis())
    }

    private companion object {
        const val SUPPRESSED = 0L
    }
}
