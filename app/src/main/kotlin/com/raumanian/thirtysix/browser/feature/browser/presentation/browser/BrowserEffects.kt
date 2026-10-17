@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A requested load: hide the keyboard, clear focus, then load, in that order. */
@Composable
internal fun BrowserEventEffects(events: Flow<AddressBarEvent>, webViewActions: WebViewActionsHandle) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    EventEffect(events) { event ->
        when (event) {
            is AddressBarEvent.LoadUrl -> {
                keyboardController?.hide()
                focusManager.clearFocus()
                webViewActions.loadUrl(event.url)
            }
        }
    }
}

@Composable
internal fun TabSessionEventEffect(events: Flow<TabSessionEvent>, webViewActions: WebViewActionsHandle) {
    EventEffect(events) { event ->
        when (event) {
            TabSessionEvent.ClearLiveHistory -> webViewActions.clearHistory()
        }
    }
}

/** A page-mode change acts at once; star and tab-limit messages never hold it up. */
@Composable
internal fun PageActionsEventEffect(
    events: Flow<PageActionsEvent>,
    webViewActions: WebViewActionsHandle,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val maxTabsMessage = stringResource(R.string.browser_max_tabs_reached)
    val bookmarkAddedMessage = stringResource(R.string.bookmark_added_snackbar)
    val bookmarkRemovedMessage = stringResource(R.string.bookmark_removed_snackbar)
    EventEffect(events) { event ->
        val message = when (event) {
            is PageActionsEvent.SetDesktopMode -> {
                webViewActions.setDesktopMode(event.enabled)
                null
            }
            is PageActionsEvent.TabLimit -> maxTabsMessage.takeIf { event.error is TabsErrorEvent.MaxTabsReached }
            is PageActionsEvent.Bookmark -> when (event.event) {
                BookmarkSnackbarEvent.Added -> bookmarkAddedMessage
                BookmarkSnackbarEvent.Removed -> bookmarkRemovedMessage
            }
        }
        if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
    }
}

/** Snackbars for hub actions that finish after the sheet has closed. */
@Composable
internal fun HubEventEffect(events: Flow<HubEvent>, snackbarHostState: SnackbarHostState) {
    val maxIncognitoMessage = stringResource(R.string.tabs_error_max_incognito_tabs_reached)
    EventEffect(events) { event ->
        when (event) {
            HubEvent.MaxIncognitoTabsReached -> snackbarHostState.showSnackbar(maxIncognitoMessage)
        }
    }
}

/** Spoken after a switch through the accessibility actions, which show no slide. */
@Composable
internal fun TabSwipeEventEffect(events: Flow<TabSwipeEvent>) {
    val view = LocalView.current
    val home = stringResource(R.string.browser_action_home)
    val incognito = stringResource(R.string.browser_a11y_switched_to_incognito_tab)
    val switchedTo = stringResource(R.string.browser_a11y_switched_to_tab)
    EventEffect(events) { event ->
        when (event) {
            is TabSwipeEvent.Announce -> {
                val tab = event.preview
                val name = tab.title.ifBlank { tab.host }.ifBlank { home }
                view.announceForAccessibility(if (tab.isIncognito) incognito else switchedTo.format(name))
            }
        }
    }
}

/** Download messages; shown apart from the collector so a showing one never holds up the next. */
@Composable
internal fun DownloadRequestEventEffect(events: Flow<DownloadSnackbarEvent>, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val startedTemplate = stringResource(R.string.downloads_started_snackbar)
    val serviceUnavailable = stringResource(R.string.downloads_service_unavailable)
    val permissionDenied = stringResource(R.string.downloads_storage_permission_denied)
    val permissionDeniedPermanently = stringResource(R.string.downloads_storage_permission_denied_permanently)
    EventEffect(events) { event ->
        val message = when (event) {
            is DownloadSnackbarEvent.Started -> startedTemplate.format(event.fileName)
            DownloadSnackbarEvent.ServiceUnavailable -> serviceUnavailable
            DownloadSnackbarEvent.StoragePermissionDenied -> permissionDenied
            DownloadSnackbarEvent.StoragePermissionPermanentlyDenied -> permissionDeniedPermanently
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
}
