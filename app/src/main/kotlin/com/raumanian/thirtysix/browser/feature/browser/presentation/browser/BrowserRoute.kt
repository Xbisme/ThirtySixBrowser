@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.LongPressActions
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PopupTabs
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.SitePermissionGate
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsViewModel

/** The page and what the person does with it, one holder per concern over the shared page store. */
class BrowserPageHolders(
    val page: PageViewModel,
    val addressBar: AddressBarViewModel,
    val findInPage: FindInPageViewModel,
    val pageActions: PageActionsViewModel,
    val tabSession: TabSessionViewModel,
)

/** The screen's tools: the hub, address suggestions, the text size and site protection. */
class BrowserToolHolders(
    val hub: HubViewModel,
    val suggestions: AddressSuggestionsViewModel,
    val pageTextSize: PageTextSizeViewModel,
    val protection: ProtectionViewModel,
)

/** What answers the page's own requests: pop-up tabs, long-press menus, site permissions and downloads. */
class PageRequestHolders(
    val popupTabs: PopupTabs,
    val longPressActions: LongPressActions,
    val permissionGate: SitePermissionGate,
    val downloadRequests: DownloadRequestViewModel,
)

@Composable
fun rememberBrowserPageHolders(
    page: PageViewModel = hiltViewModel(),
    addressBar: AddressBarViewModel = hiltViewModel(),
    findInPage: FindInPageViewModel = hiltViewModel(),
    pageActions: PageActionsViewModel = hiltViewModel(),
    tabSession: TabSessionViewModel = hiltViewModel(),
): BrowserPageHolders = remember(page, addressBar, findInPage, pageActions, tabSession) {
    BrowserPageHolders(page, addressBar, findInPage, pageActions, tabSession)
}

@Composable
fun rememberBrowserToolHolders(
    hub: HubViewModel = hiltViewModel(),
    suggestions: AddressSuggestionsViewModel = hiltViewModel(),
    pageTextSize: PageTextSizeViewModel = hiltViewModel(),
    protection: ProtectionViewModel = hiltViewModel(),
): BrowserToolHolders = remember(hub, suggestions, pageTextSize, protection) {
    BrowserToolHolders(hub, suggestions, pageTextSize, protection)
}

@Composable
fun rememberPageRequestHolders(
    popupTabs: PopupTabs = hiltViewModel(),
    longPressActions: LongPressActions = hiltViewModel(),
    permissionGate: SitePermissionGate = hiltViewModel(),
    downloadRequests: DownloadRequestViewModel = hiltViewModel(),
): PageRequestHolders = remember(popupTabs, longPressActions, permissionGate, downloadRequests) {
    PageRequestHolders(popupTabs, longPressActions, permissionGate, downloadRequests)
}

/**
 * Obtains the browser's state holders and WebView bridges, collects their state once and turns
 * their one-off events into WebView calls and snackbars.
 */
@Composable
fun BrowserRoute(
    slots: BrowserSlots,
    modifier: Modifier = Modifier,
    holders: BrowserPageHolders = rememberBrowserPageHolders(),
    tools: BrowserToolHolders = rememberBrowserToolHolders(),
    pageRequests: PageRequestHolders = rememberPageRequestHolders(),
) {
    val pageState by holders.page.uiState.collectAsStateWithLifecycle()
    val currentPage by holders.page.currentPage.collectAsStateWithLifecycle()
    val addressBarState by holders.addressBar.uiState.collectAsStateWithLifecycle()
    val actionsState by holders.pageActions.uiState.collectAsStateWithLifecycle()
    val sessionState by holders.tabSession.uiState.collectAsStateWithLifecycle()
    val tabCount by holders.page.tabCount.collectAsStateWithLifecycle()
    val activeTab by holders.page.activeTab.collectAsStateWithLifecycle()
    // Held as State, not delegated: reading `.value` here would recompose on every tick.
    val progress: State<Float> = holders.page.loadProgress.collectAsStateWithLifecycle()
    // The app-wide text size changes rarely; the WebView and the hub read it.
    val textZoomPercent by tools.pageTextSize.textZoomPercent.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }
    val snackbarHostState = remember { SnackbarHostState() }

    BrowserRouteEffects(holders, tools, pageRequests, BrowserEffectHosts(webViewActions, snackbarHostState))

    BrowserScreen(
        state = BrowserScreenState(
            page = browserUiState(currentPage, pageState, addressBarState, actionsState),
            tabCount = tabCount,
            activeTab = activeTab,
            textZoomPercent = textZoomPercent,
            loadedTabId = sessionState.loadedTabId,
        ),
        sources = browserScreenSources(holders, tools, pageRequests),
        actions = browserScreenActions(holders, tools, pageRequests),
        hosts = remember(webViewActions, snackbarHostState, slots, progress) {
            BrowserScreenHosts(webViewActions, snackbarHostState, slots, progress = { progress.value })
        },
        modifier = modifier,
    )
}

private class BrowserEffectHosts(val webViewActions: WebViewActionsHandle, val snackbarHostState: SnackbarHostState)

@Composable
private fun BrowserRouteEffects(
    holders: BrowserPageHolders,
    tools: BrowserToolHolders,
    pageRequests: PageRequestHolders,
    hosts: BrowserEffectHosts,
) {
    val webViewActions = hosts.webViewActions
    val snackbarHostState = hosts.snackbarHostState
    BrowserEventEffects(events = holders.addressBar.events, webViewActions = webViewActions)
    HubEventEffect(events = tools.hub.events, snackbarHostState = snackbarHostState)
    DownloadRequestEventEffect(events = pageRequests.downloadRequests.events, snackbarHostState = snackbarHostState)
    PageActionsEventEffect(
        events = holders.pageActions.events,
        webViewActions = webViewActions,
        snackbarHostState = snackbarHostState,
    )
    // After the per-site switch the page reloads with the new setting.
    EventEffect(tools.protection.events) { webViewActions.reload() }
}

private fun browserScreenSources(
    holders: BrowserPageHolders,
    tools: BrowserToolHolders,
    pageRequests: PageRequestHolders,
) = BrowserScreenSources(
    findInPage = holders.findInPage.state,
    suggestions = tools.suggestions.uiState,
    hasActiveDownloads = tools.hub.hasActiveDownloads,
    protectionStatus = tools.protection.status,
    protectionDashboard = tools.protection.dashboard,
    protectionHooks = tools.protection.hooks,
    desktopMode = holders.pageActions.desktopMode,
    savedState = holders.tabSession.savedState,
    popups = pageRequests.popupTabs,
    longPressActions = pageRequests.longPressActions,
    permissionGate = pageRequests.permissionGate,
)

private fun browserScreenActions(
    holders: BrowserPageHolders,
    tools: BrowserToolHolders,
    pageRequests: PageRequestHolders,
) = BrowserScreenActions(
    onPageAction = holders.page::onAction,
    onAddressBarAction = holders.addressBar::onAction,
    onFindAction = holders.findInPage::onAction,
    onDownloadAction = pageRequests.downloadRequests::onAction,
    onPageActionsAction = holders.pageActions::onAction,
    onTabSessionAction = holders.tabSession::onAction,
    tools = BrowserToolActions(
        onHubAction = tools.hub::onAction,
        onSuggestionsAction = tools.suggestions::onAction,
        onProtectionAction = tools.protection::onAction,
    ),
)
