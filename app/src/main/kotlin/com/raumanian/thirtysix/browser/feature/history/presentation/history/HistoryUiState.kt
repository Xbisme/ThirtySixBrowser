package com.raumanian.thirtysix.browser.feature.history.presentation.history

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.feature.history.domain.model.HistoryDayBucket
/**
 * [entries] stays unfiltered so the screen can tell "no history" from "no matches";
 * [searchQuery] filters [groupedEntries] only from `SEARCH_MIN_CHARS`.
 */
data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val groupedEntries: List<DayGroup> = emptyList(),
    val searchQuery: String = "",
    val isClearAllDialogVisible: Boolean = false,
    val pendingActionSheetTarget: HistoryEntry? = null,
) {
    data class DayGroup(
        val bucket: HistoryDayBucket,
        val entries: List<HistoryEntry>,
    )
}
