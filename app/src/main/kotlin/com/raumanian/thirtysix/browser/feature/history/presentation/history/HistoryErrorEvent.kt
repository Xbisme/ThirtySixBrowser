package com.raumanian.thirtysix.browser.feature.history.presentation.history

// One-shot snackbar signals; the channel does not retain past events.
sealed class HistoryErrorEvent {
    data object ClipboardCopied : HistoryErrorEvent()

    data object TabCapReached : HistoryErrorEvent()

    data class DeletionFailed(val cause: Throwable) : HistoryErrorEvent()
}
