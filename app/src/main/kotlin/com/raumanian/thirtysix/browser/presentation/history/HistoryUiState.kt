package com.raumanian.thirtysix.browser.presentation.history

import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry

/**
 * Spec 014 — immutable UI state for the HistoryScreen.
 *
 * Fields are introduced incrementally per user-story. US1 covers the listing path:
 * `entries` flow drives `groupedEntries` (reverse-chronological, bucketed); `openUrl`
 * is the consumed-once signal that pops the screen back to BrowserScreen after the
 * user taps a row (FR-010, mirroring Spec 013's `BookmarksUiState.openUrl`).
 *
 * US2 (search), US3 (long-press actions), and US4 (clear-all) extend this state with
 * `searchQuery`, `pendingActionSheetTarget`, and `isClearAllDialogVisible` respectively.
 */
data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val groupedEntries: List<DayGroup> = emptyList(),
    val searchQuery: String = "",
    val isClearAllDialogVisible: Boolean = false,
    val pendingActionSheetTarget: HistoryEntry? = null,
    val openUrl: String? = null,
) {
    /**
     * Spec 014 — rendered group, pairs a [HistoryDayBucket] (the **classification**)
     * with the entries that belong to it (already sorted reverse-chronologically).
     */
    data class DayGroup(
        val bucket: HistoryDayBucket,
        val entries: List<HistoryEntry>,
    )
}
