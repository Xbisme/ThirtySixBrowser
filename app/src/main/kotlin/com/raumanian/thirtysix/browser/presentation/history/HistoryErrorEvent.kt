package com.raumanian.thirtysix.browser.presentation.history

/**
 * Spec 014 — sealed transient one-shot signals emitted by [HistoryViewModel] over a
 * dedicated `historySnackbarEvent: SharedFlow` channel. The Composable collects each
 * event once and surfaces a localized snackbar message; the channel does not retain
 * past events.
 *
 * Per-event surface introduced incrementally:
 *  - US3 introduces [ClipboardCopied] (Copy URL action) + [TabCapReached]
 *    (Open in new tab cap-reached fallback) + [DeletionFailed] (single-row delete failure).
 */
sealed class HistoryErrorEvent {
    data object ClipboardCopied : HistoryErrorEvent()

    data object TabCapReached : HistoryErrorEvent()

    data class DeletionFailed(val cause: Throwable) : HistoryErrorEvent()
}
