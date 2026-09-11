package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Spec 014 — enforce the history retention window; Spec 016 FR-024 — the window the user chose.
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
 * Spec 016 — the cutoff comes from the persisted `UserSettings.historyRetention` instead of a
 * fixed constant. A failure to read settings propagates to the caller, whose failure boundary
 * logs it and retries next launch: the sweep never falls back to a hard-coded window.
 *
 * @param now injectable clock for tests; defaults to the current device time.
 * @return rows removed.
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
