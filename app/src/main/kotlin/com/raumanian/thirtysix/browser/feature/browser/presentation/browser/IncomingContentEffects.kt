@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.IncomingContentHooks
import com.raumanian.thirtysix.browser.core.presentation.util.findActivity
/**
 * Spec 020 FR-006 — Back on a tab opened for another app's link, with no page history left,
 * closes that tab and returns the person to the app that sent the link (research R2/R7).
 * Composed after [HomeBackHandlers]; an external tab is never `cameFromHome`, so the two are
 * never enabled together.
 */
@Composable
internal fun ExternalTabBackHandler(state: BrowserUiState, activeTabId: Long?, hooks: IncomingContentHooks) {
    val externalIds by hooks.externalTabIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isExternal = activeTabId != null && activeTabId in externalIds
    BackHandler(
        enabled = isExternal && !state.canGoBack && !state.isAddressBarFocused && !state.homeOverlay,
    ) {
        activeTabId?.let(hooks.onLeaveExternalTab)
        context.findActivity()?.moveTaskToBack(true)
    }
}

/**
 * Spec 020 research R5/R6 — loads a link into the live page when the tab cap made the app reuse
 * the active tab, and shows the entry feature's messages in this screen's snackbar host.
 */
@Composable
internal fun IncomingContentEffects(
    hooks: IncomingContentHooks,
    webViewActions: WebViewActionsHandle,
    snackbarHostState: SnackbarHostState,
) {
    val rejectedMessage = stringResource(R.string.entry_link_rejected)
    val tabLimitMessage = stringResource(R.string.entry_tab_limit_reused)
    LaunchedEffect(hooks.loadInActiveTab) {
        hooks.loadInActiveTab.collect { url -> webViewActions.loadUrl(url) }
    }
    LaunchedEffect(hooks.messages) {
        hooks.messages.collect { id ->
            val message = when (id) {
                R.string.entry_link_rejected -> rejectedMessage
                R.string.entry_tab_limit_reused -> tabLimitMessage
                else -> null
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }
}
