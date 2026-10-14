package com.raumanian.thirtysix.browser.feature.settings.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.settings.domain.model.HistoryRetentionChangeResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Writes the window before pruning: the reverse order could delete history while leaving the old
 * window recorded. A failed prune is left to the start-up sweep, which enforces the saved window.
 */
class ChangeHistoryRetentionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) {

    suspend operator fun invoke(
        retention: HistoryRetention,
        now: Long = System.currentTimeMillis(),
    ): HistoryRetentionChangeResult {
        if (settingsRepository.setHistoryRetention(retention) is Result.Error) {
            return HistoryRetentionChangeResult.NotSaved
        }
        val cutoff = now - TimeUnit.DAYS.toMillis(retention.days.toLong())
        return runCatching { historyRepository.pruneOlderThan(cutoff) }.fold(
            onSuccess = { rowsRemoved -> HistoryRetentionChangeResult.Applied(rowsRemoved) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                HistoryRetentionChangeResult.SavedPruneDeferred
            },
        )
    }
}
