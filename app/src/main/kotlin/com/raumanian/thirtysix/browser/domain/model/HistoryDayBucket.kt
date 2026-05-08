package com.raumanian.thirtysix.browser.domain.model

import java.time.LocalDate

/**
 * Spec 014 FR-008 — classification of a history entry's `visitedAt` into one of three
 * day-grouping buckets relative to the device's current local date.
 *
 * Locale-agnostic: the UI translates the bucket into a localized header string at composition
 * time using `R.string.history_day_today` / `history_day_yesterday` for the discrete cases
 * and `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` for [OnDate].
 *
 * Distinguish from `presentation.history.HistoryUiState.DayGroup` — bucket is the
 * **classification**, DayGroup is the **rendered group** that pairs a bucket with its entries.
 */
sealed class HistoryDayBucket {
    data object Today : HistoryDayBucket()

    data object Yesterday : HistoryDayBucket()

    data class OnDate(val date: LocalDate) : HistoryDayBucket()
}
