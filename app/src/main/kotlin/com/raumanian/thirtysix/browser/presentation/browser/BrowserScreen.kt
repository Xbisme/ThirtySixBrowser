@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBar
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBarCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserErrorState
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserLoadingIndicator
import com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBar
import com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBarCallbacks

/**
 * Spec 007 + Spec 008 + Spec 009 — top-level Browser screen.
 *
 * Layout (Spec 008): Material 3 [Scaffold] with always-visible
 * [NavigationBottomBar] in the `bottomBar` slot (FR-018) and the WebView
 * surface in the content slot.
 *
 * Spec 009 — adds the [AddressBar] in the [Scaffold] `topBar` slot. Always
 * visible (FR-001/02/03), even during error overlay. The submit chain dismisses
 * keyboard + clears focus *before* invoking `WebViewActionsHandle.loadUrl`
 * (FR-013a). The address-bar text + focus state survive rotation by virtue of
 * being held inside the ViewModel-scoped `BrowserUiState` (FR-027).
 *
 * Spec 008 system-back integration: [PredictiveBackHandler] is enabled only
 * when `state.canGoBack == true`. Spec 009 adds an in-screen [BackHandler]
 * ahead of it that consumes the back gesture iff the address bar is focused
 * — clears focus + dismisses keyboard, leaving session-history back to the
 * predictive handler on the next press (FR-026 / research R6). The Android
 * IME platform contract handles the standard "keyboard is open → back closes
 * keyboard" case automatically; this in-screen `BackHandler` covers the niche
 * "keyboard hidden but bar still focused" path.
 */
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = state.isAddressBarFocused) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    PredictiveBackHandler(enabled = state.canGoBack && !state.isAddressBarFocused) { progress ->
        progress.collect { /* no-op — system renders preview on Android 14+ */ }
        webViewActions.goBack()
    }

    val addressBarCallbacks = rememberAddressBarCallbacks(viewModel, webViewActions)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AddressBar(state = state, callbacks = addressBarCallbacks) },
        bottomBar = {
            NavigationBottomBar(
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isLoading = state.loadingState is LoadingState.Loading,
                callbacks = rememberBottomBarCallbacks(state, webViewActions, viewModel::onLoadStopped),
            )
        },
    ) { padding ->
        BrowserScaffoldContent(
            state = state,
            viewModel = viewModel,
            webViewActions = webViewActions,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/**
 * Spec 009 — extracted from [BrowserScreen]'s `content` slot to keep the host
 * function under detekt's `LongMethod` threshold (60 lines). Renders the
 * always-mounted [BrowserWebView] plus the conditional loading-indicator and
 * error-state overlays. Spec 008 ordering preserved: WebView at the back,
 * loading on top, error fully covers (FR-001/02/03 — bar always visible
 * because it lives in the Scaffold's `topBar` slot, not here).
 */
@Composable
private fun BrowserScaffoldContent(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        BrowserWebView(
            state = state,
            homeUrl = viewModel.homeUrl,
            actions = webViewActions,
            callbacks = BrowserWebViewCallbacks(
                onLoadStarted = viewModel::onLoadStarted,
                onProgressChanged = viewModel::onProgressChanged,
                onLoadFinished = viewModel::onLoadFinished,
                onLoadFailed = viewModel::onLoadFailed,
            ),
            navigationCallbacks = BrowserNavigationCallbacks(
                onUrlChange = viewModel::onUrlChanged,
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

/**
 * Spec 009 — wires the address-bar callbacks. The `onSubmit` lambda chain
 * implements FR-013a: dismiss keyboard → clear focus → load URL, in that
 * order. The dismiss + clear happen inside the URL-consumer lambda passed
 * to [BrowserViewModel.onAddressBarSubmit] so they only execute on
 * non-empty submits (the ViewModel skips invocation on `Empty`).
 */
@Composable
private fun rememberAddressBarCallbacks(
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
): AddressBarCallbacks {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return AddressBarCallbacks(
        onTextChange = viewModel::onAddressBarTextChange,
        onFocusChange = viewModel::onAddressBarFocusChange,
        onSubmit = {
            viewModel.onAddressBarSubmit { url ->
                keyboardController?.hide()
                focusManager.clearFocus()
                webViewActions.loadUrl(url)
            }
        },
        onClear = viewModel::onAddressBarClear,
    )
}
