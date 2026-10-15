package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.AddressBarCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.LongPressHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsAction

@Composable
internal fun rememberHomeCallbacks(
    actions: BrowserScreenActions,
    webViewActions: WebViewActionsHandle,
    slots: BrowserSlots,
): HomeCallbacks = remember(actions, webViewActions, slots) {
    HomeCallbacks(
        onSearchSubmit = { input -> actions.onAddressBarAction(AddressBarAction.HomeSubmit(input)) },
        onOpenUrl = { url ->
            actions.onAddressBarAction(AddressBarAction.NavigateFromHome)
            webViewActions.loadUrl(url)
        },
        onSeeAllHistory = slots.destinations.openHistory,
        onNewIncognitoTab = { actions.tools.onHubAction(HubAction.NewIncognitoTab) },
        // Home's field starts empty, so there is no page address to compare with.
        onSearchTextChange = { text ->
            actions.tools.onSuggestionsAction(AddressSuggestionsAction.TextChanged(text, currentPageUrl = null))
        },
        onSearchFocusChange = { focused ->
            actions.tools.onSuggestionsAction(AddressSuggestionsAction.FocusChanged(focused))
        },
    )
}

/** A submit reaches the view model, whose load event dismisses the keyboard and clears focus first. */
@Composable
internal fun rememberAddressBarCallbacks(
    onAction: (AddressBarAction) -> Unit,
    onSuggestionsAction: (AddressSuggestionsAction) -> Unit,
    currentUrl: () -> String,
): AddressBarCallbacks {
    val pageUrl by rememberUpdatedState(currentUrl)
    return remember(onAction, onSuggestionsAction) {
        val submit = { onAction(AddressBarAction.Submit) }
        AddressBarCallbacks(
            onTextChange = { text ->
                onAction(AddressBarAction.TextChange(text))
                onSuggestionsAction(AddressSuggestionsAction.TextChanged(text, currentPageUrl = pageUrl()))
            },
            onFocusChange = { focused ->
                onAction(AddressBarAction.FocusChange(focused))
                onSuggestionsAction(AddressSuggestionsAction.FocusChanged(focused))
            },
            onSubmit = submit,
            onClear = { onAction(AddressBarAction.Clear) },
            // A chosen suggestion is submitted exactly like a typed address.
            onSuggestion = { url ->
                onAction(AddressBarAction.TextChange(url))
                submit()
            },
        )
    }
}

/** Page and long-press downloads both pass the storage-permission gate, which API 29+ skips. */
@Composable
internal fun rememberDownloadRequests(
    onAction: (DownloadRequestAction) -> Unit,
): (String, String, String?, String?) -> Unit = rememberDownloadRequestHandler(
    onGrant = { url, userAgent, disposition, mimeType ->
        onAction(DownloadRequestAction.Requested(url, userAgent, disposition, mimeType))
    },
    onDeny = { permanently -> onAction(DownloadRequestAction.StoragePermissionDenied(permanently)) },
)

@Composable
internal fun rememberLongPressHooks(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
): LongPressHooks {
    val context = LocalContext.current
    val longPressDownload = rememberDownloadRequests(actions.onDownloadAction)
    val currentIncognito by rememberUpdatedState(state.page.isIncognito)
    return remember(sources.longPressActions, hosts.snackbarHostState, longPressDownload) {
        LongPressHooks(
            actions = sources.longPressActions,
            snackbarHostState = hosts.snackbarHostState,
            isIncognito = { currentIncognito },
            onDownload = { url -> longPressDownload(url, WebSettings.getDefaultUserAgent(context), null, null) },
        )
    }
}
