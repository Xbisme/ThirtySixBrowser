package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/**
 * State of the WebView's current load operation. Sealed (not enum) because [Failed] carries
 * an [ErrorReason].
 *
 * Spec 019 (research R8, Constitution v1.4.0 §V rule 3) — [Loading] no longer carries the
 * progress fraction. Progress changes many times per load; kept here it made every tick a new
 * `BrowserUiState` and recomposed the whole browser chrome. It now lives in
 * `BrowserViewModel.loadProgress`, read only by the progress line in its draw phase.
 *
 * Transitions live in [BrowserViewModel]; do not mutate from anywhere else.
 */
sealed class LoadingState {
    /** No load in progress yet. Initial state immediately after VM construction. */
    data object Idle : LoadingState()

    /** Page load in progress. How far along is `BrowserViewModel.loadProgress`. */
    data object Loading : LoadingState()

    /** Load completed successfully (`onPageFinished` or `onProgressChanged(100)`). */
    data object Loaded : LoadingState()

    /** Main-frame load failed; mapped from `WebViewClient` error callbacks (US3 / T031). */
    data class Failed(val reason: ErrorReason) : LoadingState()
}
