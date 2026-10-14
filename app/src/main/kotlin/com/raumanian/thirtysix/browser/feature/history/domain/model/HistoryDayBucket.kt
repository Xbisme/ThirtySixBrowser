package com.raumanian.thirtysix.browser.feature.history.domain.model

import java.time.LocalDate

/**
 * Which day group a visit falls into, relative to the device's local date. Locale-agnostic:
 * the UI renders the header. `HistoryUiState.DayGroup` pairs a bucket with its entries.
 */
sealed class HistoryDayBucket {
    data object Today : HistoryDayBucket()

    data object Yesterday : HistoryDayBucket()

    data class OnDate(val date: LocalDate) : HistoryDayBucket()
}
