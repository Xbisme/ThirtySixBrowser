package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.HistoryRetentionChangeResult
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Spec 016 FR-020 – FR-023 — save a new history retention window and enforce it at once.
 *
 * Called only after the view model has the FR-021 confirmation for a shorter window;
 * lengthening needs none.
 *
 *  1. Write the window. A failed write returns [HistoryRetentionChangeResult.NotSaved] and
 *     deletes nothing.
 *  2. Prune history older than the window. A failed prune returns
 *     [HistoryRetentionChangeResult.SavedPruneDeferred] — the window is already recorded, so
 *     the start-up sweep enforces it next launch (FR-024).
 *  3. Otherwise return [HistoryRetentionChangeResult.Applied].
 *
 * Write-before-prune is deliberate (data-model §2, research R8): the reverse order could delete
 * history while leaving the old window recorded. The prune runs on every change and simply
 * removes nothing extra when lengthening. Coordinating two repositories is the use-case layer's
 * job (Constitution §IV). Cancellation is rethrown, never mapped to an outcome.
 */
class ChangeHistoryRetentionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) {

    /** @param now injectable clock for tests. */
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
