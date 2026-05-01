@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserErrorState
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserLoadingIndicator
import com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBar
import com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBarCallbacks

/**
 * Spec 007 + Spec 008 — top-level Browser screen.
 *
 * Layout (Spec 008): Material 3 [Scaffold] with always-visible
 * [NavigationBottomBar] in the `bottomBar` slot (FR-018) and the WebView
 * surface in the content slot. WebView is rendered in ALL non-Failed states;
 * loading + error UIs overlay on top. The `Failed` overlay obscures the WebView
 * visually but the WebView remains mounted so subsequent Reload / Back /
 * Forward / Home actions through `WebViewActionsHandle` work without
 * re-creating the WebView (preserves session history through error→recover).
 *
 * Spec 008 system-back integration: [PredictiveBackHandler] is enabled only
 * when `state.canGoBack == true`. When disabled, the platform default takes
 * over (FR-011 — finish screen / app at root). On Android 14+, the system
 * renders a predictive preview during the gesture; on API 24–33 the gesture
 * commits without preview but the back action runs identically. The `progress`
 * flow is collected purely to detect commit (flow completes) vs cancel
 * (flow throws `CancellationException`, which propagates naturally — no
 * try/catch needed; the runtime + PredictiveBackHandler infrastructure handle
 * cancellation correctly when the action lambda is never reached).
 */
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }

    PredictiveBackHandler(enabled = state.canGoBack) { progress ->
        progress.collect { /* no-op — system renders preview on Android 14+ */ }
        // Flow completed normally → gesture committed → navigate back.
        // CancellationException (gesture cancelled) propagates without reaching here.
        webViewActions.goBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBottomBar(
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isLoading = state.loadingState is LoadingState.Loading,
                callbacks = rememberBottomBarCallbacks(state, webViewActions, viewModel::onLoadStopped),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BrowserWebView(
                state = state,
                homeUrl = viewModel.homeUrl,
                actions = webViewActions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = viewModel::onLoadStarted,
                    onProgressChanged = viewModel::onProgressChanged,
                    onLoadFinished = viewModel::onLoadFinished,
                    onLoadFailed = viewModel::onLoadFailed,
                    onCanGoBackChange = viewModel::onCanGoBackChanged,
                    onCanGoForwardChange = viewModel::onCanGoForwardChanged,
                ),
                modifier = Modifier.fillMaxSize(),
            )
            (state.loadingState as? LoadingState.Loading)?.let { loading ->
                BrowserLoadingIndicator(
                    progress = loading.progress,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            (state.loadingState as? LoadingState.Failed)?.let { failed ->
                BrowserErrorState(
                    reason = failed.reason,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Spec 008 — extracted to keep [BrowserScreen] under detekt's `LongMethod`
 * threshold. Builds the four click lambdas the bottom bar dispatches:
 * Back/Forward route directly through [WebViewActionsHandle]; Reload/Stop
 * dispatches conditionally on [BrowserUiState.loadingState] (Stop semantic
 * during Loading also flips state via [onStopRequested] so the loading
 * indicator hides immediately per FR-006); Home calls
 * [WebViewActionsHandle.loadHome] which the WebView factory wired to
 * `loadUrl(homeUrl)`.
 */
@Composable
private fun rememberBottomBarCallbacks(
    state: BrowserUiState,
    webViewActions: WebViewActionsHandle,
    onStopRequested: () -> Unit,
): NavigationBottomBarCallbacks = NavigationBottomBarCallbacks(
    onBack = { webViewActions.goBack() },
    onForward = { webViewActions.goForward() },
    onReloadOrStop = {
        if (state.loadingState is LoadingState.Loading) {
            webViewActions.stopLoading()
            onStopRequested()
        } else {
            webViewActions.reload()
        }
    },
    onHome = { webViewActions.loadHome() },
)
