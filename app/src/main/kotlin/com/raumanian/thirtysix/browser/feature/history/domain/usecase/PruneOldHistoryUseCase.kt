package com.raumanian.thirtysix.browser.feature.history.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Enforces the retention window the user chose, once per process start; the History screen
 * observes the whole table, so unbounded growth would cost memory. Rows are deleted, not hidden,
 * so search never reports "no matches" for a row that still exists.
 * A settings read failure propagates: the sweep never falls back to a hard-coded window.
 */
class PruneOldHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): Int {
        val retention = settingsRepository.observeSettings().first().historyRetention
        val cutoff = now - TimeUnit.DAYS.toMillis(retention.days.toLong())
        return repository.pruneOlderThan(cutoff)
    }
}
