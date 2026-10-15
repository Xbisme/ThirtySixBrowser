@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** One-off browser events: loads and page-mode changes act at once; messages never hold them up. */
@Composable
internal fun BrowserEventEffects(
    events: Flow<BrowserEvent>,
    webViewActions: WebViewActionsHandle,
    snackbarHostState: SnackbarHostState,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val maxTabsMessage = stringResource(R.string.browser_max_tabs_reached)
    val bookmarkAddedMessage = stringResource(R.string.bookmark_added_snackbar)
    val bookmarkRemovedMessage = stringResource(R.string.bookmark_removed_snackbar)

    EventEffect(events) { event ->
        val message = when (event) {
            is BrowserEvent.LoadUrl -> {
                keyboardController?.hide()
                focusManager.clearFocus()
                webViewActions.loadUrl(event.url)
                null
            }
            is BrowserEvent.SetDesktopMode -> {
                webViewActions.setDesktopMode(event.enabled)
                null
            }
            is BrowserEvent.TabLimit -> maxTabsMessage.takeIf { event.error is TabsErrorEvent.MaxTabsReached }
            is BrowserEvent.Bookmark -> when (event.event) {
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
