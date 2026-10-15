package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/**
 * The WebView's current load. [Loading] carries no progress fraction: that lives in
 * `PageViewModel.loadProgress`, so progress ticks do not recompose the browser chrome.
 */
sealed class LoadingState {
    data object Idle : LoadingState()

    data object Loading : LoadingState()

    /** `onPageFinished` or `onProgressChanged(100)`, whichever comes first. */
    data object Loaded : LoadingState()

    /** Main-frame failure; sub-frame errors never get here. */
    data class Failed(val reason: ErrorReason) : LoadingState()
}
