package com.raumanian.thirtysix.browser.presentation.history

/**
 * Spec 014 — sealed transient one-shot signals emitted by [HistoryViewModel] over a
 * dedicated `historySnackbarEvent: SharedFlow` channel. The Composable collects each
 * event once and surfaces a localized snackbar message; the channel does not retain
 * past events.
 *
 * Event map:
 *  - [ClipboardCopied] — FR-021, confirms the Copy URL action reached the clipboard.
 *  - [TabCapReached] — FR-019a, the normal-tab cap blocked "Open in new tab".
 *  - [DeletionFailed] — FR-020 / FR-025, a single-row delete or a clear-all wipe threw.
 */
sealed class HistoryErrorEvent {
    data object ClipboardCopied : HistoryErrorEvent()

    data object TabCapReached : HistoryErrorEvent()

    data class DeletionFailed(val cause: Throwable) : HistoryErrorEvent()
}
