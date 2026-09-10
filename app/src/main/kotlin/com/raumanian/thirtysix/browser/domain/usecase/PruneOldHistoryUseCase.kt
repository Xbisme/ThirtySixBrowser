package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Spec 014 — enforce the [BrowserLimits.MAX_HISTORY_DAYS] retention window.
 *
 * Runs once per process start. Without it the history table grows without bound, and
 * because the History screen observes the *whole* table, its memory cost grows with it:
 * ~300 B per row measured on an API 36 emulator, so ~30 MB at 100 000 rows — a real
 * risk on a minSdk-24 device.
 *
 * Deleting rather than merely hiding old rows is the deliberate choice: it keeps the
 * invariant that anything absent from the list is genuinely absent from the database,
 * so search never reports "no matches" for a row that still exists.
 *
 * @param now injectable clock for tests; defaults to the current device time.
 * @return rows removed.
 */
class PruneOldHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - TimeUnit.DAYS.toMillis(BrowserLimits.MAX_HISTORY_DAYS.toLong())
        return repository.pruneOlderThan(cutoff)
    }
}
