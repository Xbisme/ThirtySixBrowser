@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Back history goes to the WebView; a focused address bar consumes Back to clear focus first. */
@Composable
internal fun BrowserBackHandlers(
    state: BrowserUiState,
    webViewActions: WebViewActionsHandle,
) {
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
}

/**
 * Back over the Home overlay returns to the page underneath; Back on a page opened from Home,
 * with no history left, returns to Home. Composed after [BrowserBackHandlers] to take precedence.
 */
@Composable
internal fun HomeBackHandlers(state: BrowserUiState, onAction: (AddressBarAction) -> Unit) {
    BackHandler(enabled = state.homeOverlay) { onAction(AddressBarAction.HomeOverlayDismissed) }
    BackHandler(
        enabled = !state.canGoBack && state.cameFromHome && !state.isIncognito && !state.isHomeContent,
    ) { onAction(AddressBarAction.BackWithoutHistory) }
}
