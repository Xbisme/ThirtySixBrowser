package com.raumanian.thirtysix.browser.feature.settings.domain.model

sealed interface HistoryRetentionChangeResult {

    data class Applied(val rowsRemoved: Int) : HistoryRetentionChangeResult

    /** Saved, but pruning failed; the start-up sweep enforces the window on the next launch. */
    data object SavedPruneDeferred : HistoryRetentionChangeResult

    /** Nothing was deleted. */
    data object NotSaved : HistoryRetentionChangeResult
}
