package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Immutable

/**
 * Spec 022 data-model §3 — find in page, for the active tab while the find bar is open.
 *
 * [current] is 1-based and 0 when there is no match; [total] is 0 when there is none
 * (`current ≤ total`, I-6). Exposed by [BrowserViewModel.findInPage] as its own flow, never as
 * part of [BrowserUiState]: results arrive on every keystroke and while the page is still
 * loading, and only the find bar should recompose for them (Constitution §V rule 3).
 */
@Immutable
data class FindInPageState(
    val isOpen: Boolean = false,
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
) {
    /** FR-009 — the count shows only once there is something to count. */
    val counterVisible: Boolean get() = query.isNotEmpty()

    /** FR-008 — Previous and Next only move between matches that exist. */
    val canStep: Boolean get() = total > 0
}
