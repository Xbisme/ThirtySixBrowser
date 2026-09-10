package com.raumanian.thirtysix.browser.presentation.history

import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry

/**
 * Spec 014 — immutable UI state for the HistoryScreen.
 *
 * Field map by user story:
 *  - **US1** — `entries` (the full observed list) drives `groupedEntries`
 *    (reverse-chronological, day-bucketed); `openUrl` is the consumed-once signal that
 *    pops the screen back to BrowserScreen after the user taps a row or successfully
 *    opens one in a new tab (FR-010 / FR-019, mirroring Spec 013's `BookmarksUiState.openUrl`).
 *  - **US2** — `searchQuery` holds the raw user-typed text. It filters `groupedEntries`
 *    only once it reaches `BrowserLimits.SEARCH_MIN_CHARS` (FR-011a); `entries` always
 *    stays unfiltered so the screen can tell "no history at all" apart from "no matches".
 *  - **US3** — `pendingActionSheetTarget` is non-null while the long-press sheet is open.
 *  - **US4** — `isClearAllDialogVisible` gates the destructive confirmation dialog.
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
