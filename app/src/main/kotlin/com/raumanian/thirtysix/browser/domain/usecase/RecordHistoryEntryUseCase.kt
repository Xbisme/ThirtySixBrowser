package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Spec 014 — record one history entry for a successful page load.
 *
 * Enforces the FR-002 incognito-suppression rule: when [isIncognito] is true the call
 * is a no-op. Otherwise persists the visit at the current device time. Caller (the
 * `BrowserViewModel`) MUST invoke this only at the page-finish boundary; failed loads
 * MUST NOT call into here (FR-003).
 *
 * @return the new row id, or `0L` when the visit was suppressed as incognito. The
 *  caller keeps the id so a late-arriving page title can be patched onto the row via
 *  [UpdateHistoryEntryTitleUseCase].
 */
class RecordHistoryEntryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(url: String, title: String, isIncognito: Boolean): Long {
        if (isIncognito) return SUPPRESSED
        return repository.recordVisit(url, title, System.currentTimeMillis())
    }

    private companion object {
        /** Sentinel returned when FR-002 suppressed the write. */
        const val SUPPRESSED = 0L
    }
}
