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
 * [com.raumanian.thirtysix.browser.di.UrlConfigModule]) so the instrumented
 * test can swap in an invalid host via `@TestInstallIn` (T036a) for the
 * offline-error scenario without disabling the emulator's network.
 *
 * Phasing:
 * - US1 (this file initial version) ships only the three happy-path entry
 *   points: [onLoadStarted], [onProgressChanged], [onLoadFinished].
 * - US3 (T030) ADDS `onLoadFailed(reason: ErrorReason)`. The method is
 *   deliberately absent here because [ErrorReason] (T028) and
 *   [LoadingState.Failed] (T029) are not yet declared.
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

    private companion object {
        const val MAX_PROGRESS: Int = 100
        const val MAX_PROGRESS_F: Float = 100f
    }
}
