package com.raumanian.thirtysix.browser.presentation.browser

/**
 * State of the WebView's current load operation. Sealed (not enum) because
 * [Loading] carries per-instance progress and (when added in US3 / T029)
 * [Failed] carries an [ErrorReason].
 *
 * Transitions live in [BrowserViewModel]; do not mutate from anywhere else.
 */
sealed class LoadingState {
    /** No load in progress yet. Initial state immediately after VM construction. */
    data object Idle : LoadingState()

    /**
     * Page load in progress. [progress] in `[0f, 1f]` from
     * `WebChromeClient.onProgressChanged(view, newProgress)` mapped via
     * `newProgress / 100f`. Indeterminate loads are represented as
     * `Loading(progress = 0f)`.
     */
    data class Loading(val progress: Float) : LoadingState() {
        init {
            require(progress in 0f..1f) { "progress must be in [0,1], got=$progress" }
        }
    }

    /** Load completed successfully (`onPageFinished` or `onProgressChanged(100)`). */
    data object Loaded : LoadingState()

    /** Main-frame load failed; mapped from `WebViewClient` error callbacks (US3 / T031). */
    data class Failed(val reason: ErrorReason) : LoadingState()
}
