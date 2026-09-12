// Spec 016 — history retention window (FR-020 – FR-024).

package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits

/**
 * Spec 016 FR-020 — how long browsing history is kept.
 *
 * Exactly four windows and no unlimited one: "keep forever" is unrepresentable rather than
 * merely unoffered, because Spec 014 measured an unbounded history table as a real memory
 * risk on minSdk-24 devices — the History screen holds the whole table in memory.
 *
 * Persisted as [days] (data-model §1). [fromDaysOrDefault] maps anything else — a missing
 * key, a corrupt value, a value written by a future build — back to the default, so no
 * stored value can ever produce an unbounded or out-of-set window.
 */
enum class HistoryRetention(val days: Int) {
    Days7(BrowserLimits.HISTORY_RETENTION_DAYS_7),
    Days30(BrowserLimits.HISTORY_RETENTION_DAYS_30),
    Days90(BrowserLimits.HISTORY_RETENTION_DAYS_90),
    Days180(BrowserLimits.HISTORY_RETENTION_DAYS_180),
    ;

    /**
     * FR-021 — whether moving from [other] to this window deletes history, and so needs the
     * user's confirmation first. Equal is not shorter, which makes re-selecting the current
     * window a no-op (FR-006).
     */
    fun isShorterThan(other: HistoryRetention): Boolean = days < other.days

    companion object {
        /** Decodes a stored day count; anything outside the four windows yields the default. */
        fun fromDaysOrDefault(value: Int?): HistoryRetention =
            entries.firstOrNull { it.days == value } ?: AppDefaults.HISTORY_RETENTION
    }
}
