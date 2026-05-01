package com.raumanian.thirtysix.browser.presentation.browser

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Spec 007 — owns [BrowserUiState] and translates WebView client/chrome
 * callbacks into state transitions.
 *
 * The default URL is Hilt-injected (production binding in
 * [com.raumanian.thirtysix.browser.di.UrlConfigModule]). Instrumented tests
 * construct this ViewModel directly with an explicit URL parameter (see
 * `BrowserScreenOfflineErrorTest` / `BrowserScreenInstrumentedTest` patterns)
 * — `@TestInstallIn` was tried for Spec 007 but turned out unreliable in
 * androidTest; manual construction is the project's working pattern.
 *
 * Spec 008 extension:
 * - Exposes [homeUrl] getter so the UI layer (NavigationBottomBar Home tap +
 *   `WebViewActionsHandle.loadHome`) can read the same constant the WebView
 *   was initialized with, without re-injecting Hilt at the Composable layer.
 * - Adds [onCanGoBackChanged] / [onCanGoForwardChanged] state mutators called
 *   from `BrowserWebViewCallbacks` after every `WebViewClient.doUpdateVisitedHistory`.
 * - Adds [onLoadStopped] for the Stop affordance (FR-006): user-initiated cancel
 *   while `LoadingState.Loading` → transition to `LoadingState.Loaded`. The
 *   loading indicator hides; partial page state remains as-is in the WebView.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
) : ViewModel() {

    private val _uiState: MutableStateFlow<BrowserUiState> = MutableStateFlow(
        BrowserUiState(
            currentUrl = defaultHomeUrl,
            loadingState = LoadingState.Idle,
        ),
    )

    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /**
     * Spec 008 — exposed for the Home affordance + `WebViewActionsHandle.loadHome`
     * so the UI layer doesn't need its own Hilt injection of the URL constant.
     */
    val homeUrl: String get() = defaultHomeUrl

    /** Called from `BrowserWebViewClient.onPageStarted`. */
    fun onLoadStarted(url: String) {
        _uiState.update {
            it.copy(currentUrl = url, loadingState = LoadingState.Loading(progress = 0f))
        }
    }

    /**
     * Called from `BrowserChromeClient.onProgressChanged`. [newProgress] is
     * 0..100 from WebView; clamped defensively. Progress 100 transitions to
     * [LoadingState.Loaded] (idempotent with [onLoadFinished]).
     */
    fun onProgressChanged(newProgress: Int) {
        val clamped = newProgress.coerceIn(0, MAX_PROGRESS)
        _uiState.update { current ->
            if (clamped >= MAX_PROGRESS) {
                if (current.loadingState is LoadingState.Loaded) {
                    current
                } else {
                    current.copy(loadingState = LoadingState.Loaded)
                }
            } else {
                current.copy(loadingState = LoadingState.Loading(progress = clamped / MAX_PROGRESS_F))
            }
        }
    }

    /** Called from `BrowserWebViewClient.onPageFinished`. Idempotent vs progress(100). */
    fun onLoadFinished(url: String) {
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loaded && current.currentUrl == url) {
                current
            } else {
                current.copy(currentUrl = url, loadingState = LoadingState.Loaded)
            }
        }
    }

    /**
     * Called from `BrowserWebViewClient` error overrides for main-frame failures
     * (US3 / T031). Sub-frame errors are filtered upstream before reaching here.
     */
    fun onLoadFailed(reason: ErrorReason) {
        _uiState.update { it.copy(loadingState = LoadingState.Failed(reason)) }
    }

    /**
     * Spec 008 — called from `BrowserWebViewClient.doUpdateVisitedHistory`
     * after every history-mutating commit (page nav, back/forward, fragment
     * change, History API push). Source of truth: `WebView.canGoBack()`.
     */
    fun onCanGoBackChanged(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    /**
     * Spec 008 — symmetric to [onCanGoBackChanged]. Source of truth:
     * `WebView.canGoForward()`.
     */
    fun onCanGoForwardChanged(canGoForward: Boolean) {
        _uiState.update { it.copy(canGoForward = canGoForward) }
    }

    /**
     * Spec 008 — called from the bottom-bar Reload/Stop click handler when the
     * current state is [LoadingState.Loading]. Transitions to [LoadingState.Loaded]
     * so the loading indicator hides immediately (FR-006: page settles at
     * whatever state it had reached). Idempotent; non-Loading states pass
     * through unchanged.
     */
    fun onLoadStopped() {
        _uiState.update { current ->
            if (current.loadingState is LoadingState.Loading) {
                current.copy(loadingState = LoadingState.Loaded)
            } else {
                current
            }
        }
    }

    private companion object {
        const val MAX_PROGRESS: Int = 100
        const val MAX_PROGRESS_F: Float = 100f
    }
}
