package com.raumanian.thirtysix.browser.feature.history.presentation.history

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry

sealed interface HistoryAction {
    data class EntryTap(val entry: HistoryEntry) : HistoryAction

    data class LongPressEntry(val entry: HistoryEntry) : HistoryAction

    data object ActionSheetDismiss : HistoryAction

    data class OpenInNewTab(val entry: HistoryEntry) : HistoryAction

    data class DeleteEntry(val entry: HistoryEntry) : HistoryAction

    data class CopyUrl(val entry: HistoryEntry) : HistoryAction

    data class SearchQueryChange(val query: String) : HistoryAction

    data object SearchQueryClear : HistoryAction

    data object ClearAllRequested : HistoryAction

    data object ClearAllCancelled : HistoryAction

    data object ClearAllConfirmed : HistoryAction
}
