package com.raumanian.thirtysix.browser.presentation.browser

import androidx.lifecycle.ViewModel
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
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
// 12 callback methods (load lifecycle + navigation + address bar); each is a thin
// state-mutator (`_uiState.update { ... }`) with no business logic. Splitting this
// into smaller ViewModels is premature — every method shares the same single source
// of truth (`BrowserUiState`). Reconsider if Spec 011 / 012 introduce orthogonal
// state surfaces.
@HiltViewModel
@Suppress("TooManyFunctions")
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

    /**
     * Called from `BrowserWebViewClient.onPageStarted`.
     *
     * Spec 009 narrows the responsibility: this method no longer mutates
     * [BrowserUiState.currentUrl]. The Spec 009 [onUrlChanged] is the single
     * source of truth for `currentUrl` mutation; in production, `onPageStarted`
     * fires `onUrlChange` BEFORE `onLoadStarted` so the URL is already up to
     * date when this callback runs. The `url` parameter is retained on the
     * signature for binary-compatibility with existing call sites and to
     * preserve symmetry with [onLoadFinished].
     */
    @Suppress("UNUSED_PARAMETER")
    fun onLoadStarted(url: String) {
        _uiState.update {
            it.copy(loadingState = LoadingState.Loading(progress = 0f))
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

    // ---------- Spec 009 — Address Bar / Omnibox ----------

    /** Update the address-bar text on every keystroke from the Composable. */
    fun onAddressBarTextChange(newText: String) {
        _uiState.update { it.copy(addressBarText = newText) }
    }

    /** Update the address-bar focus flag from the Composable's focus listener. */
    fun onAddressBarFocusChange(focused: Boolean) {
        _uiState.update { it.copy(isAddressBarFocused = focused) }
    }

    /**
     * Spec 009 — classify the current [BrowserUiState.addressBarText] and
     * dispatch to [loadUrl] (the imperative `WebViewActionsHandle.loadUrl`
     * lambda passed by the Composable closure). Returns `true` iff a non-empty
     * submit was made; the Composable uses this to decide whether to dismiss
     * focus + keyboard (FR-013a).
     *
     * Empty / whitespace-only input is a no-op (FR-012) and returns `false`.
     * Spec 010 will refactor the [AddressBarSubmitResult.Query] branch into a
     * `SearchEngineRepository`; until then it inlines `URLEncoder.encode(...)`
     * + the [com.raumanian.thirtysix.browser.core.constants.UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE].
     */
    fun onAddressBarSubmit(loadUrl: (String) -> Unit): Boolean {
        val raw = _uiState.value.addressBarText
        return when (val classified = classifyAddressBarInput(raw)) {
            AddressBarSubmitResult.Empty -> false
            is AddressBarSubmitResult.Url -> {
                loadUrl(classified.target)
                true
            }
            is AddressBarSubmitResult.Query -> {
                val encoded = URLEncoder.encode(classified.text, Charsets.UTF_8.name())
                val searchUrl = String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
                loadUrl(searchUrl)
                true
            }
        }
    }

    /** Empty the address-bar text while preserving focus (FR-021). */
    fun onAddressBarClear() {
        _uiState.update { it.copy(addressBarText = "") }
    }

    /**
     * Spec 009 — single source of truth for [BrowserUiState.currentUrl] mutation.
     * Wired (in Phase 5 / US3) to fire from `onPageStarted` AND
     * `doUpdateVisitedHistory`, which gives the live URL trace through any
     * redirect chain (FR-019 / FR-019a / FR-019b). [onLoadStarted] no longer
     * touches `currentUrl` after the Spec 009 refactor — the responsibility
     * narrowed to loading-state transitions only.
     */
    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(currentUrl = url) }
    }

    private companion object {
        const val MAX_PROGRESS: Int = 100
        const val MAX_PROGRESS_F: Float = 100f
    }
}
