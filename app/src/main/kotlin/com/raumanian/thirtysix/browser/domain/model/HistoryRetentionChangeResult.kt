package com.raumanian.thirtysix.browser.domain.model

/** Spec 016 FR-020 – FR-023 — the outcome of changing the history retention window. */
sealed interface HistoryRetentionChangeResult {

    /** The window was saved and history older than it was deleted. */
    data class Applied(val rowsRemoved: Int) : HistoryRetentionChangeResult

    /**
     * The window was saved, but deleting older history failed. The start-up sweep enforces
     * the window on the next launch (FR-024); the screen tells the user so (FR-043).
     */
    data object SavedPruneDeferred : HistoryRetentionChangeResult

    /** The window could not be saved. Nothing was deleted. */
    data object NotSaved : HistoryRetentionChangeResult
}
