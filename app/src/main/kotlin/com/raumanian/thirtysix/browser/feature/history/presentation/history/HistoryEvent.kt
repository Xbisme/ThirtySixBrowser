package com.raumanian.thirtysix.browser.feature.history.presentation.history

sealed interface HistoryEvent {
    data class OpenUrl(val url: String) : HistoryEvent

    data class Snackbar(val event: HistoryErrorEvent) : HistoryEvent
}
