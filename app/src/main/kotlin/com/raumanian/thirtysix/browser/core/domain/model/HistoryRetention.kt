package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
/**
 * No unlimited window: the History screen holds the whole table in memory. Any stored value
 * outside the four windows decodes to the default, so none can be unbounded.
 */
enum class HistoryRetention(val days: Int) {
    Days7(BrowserLimits.HISTORY_RETENTION_DAYS_7),
    Days30(BrowserLimits.HISTORY_RETENTION_DAYS_30),
    Days90(BrowserLimits.HISTORY_RETENTION_DAYS_90),
    Days180(BrowserLimits.HISTORY_RETENTION_DAYS_180),
    ;

    /** Whether moving from [other] deletes history and so needs confirmation. */
    fun isShorterThan(other: HistoryRetention): Boolean = days < other.days

    companion object {
        fun fromDaysOrDefault(value: Int?): HistoryRetention =
            entries.firstOrNull { it.days == value } ?: AppDefaults.HISTORY_RETENTION
    }
}
