@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserBottomBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserBottomBarCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserTopBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.BrowserPageRequests
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.FullscreenOverlay
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PopupEffects
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.rememberPageSession
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.BrowserAddressSuggestions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The browser: the WebView fills the screen and the bars float over it. Scroll offsets and load
 * progress are read only inside `graphicsLayer`/draw lambdas, so scrolling and loading recompose nothing.
 */
@Composable
internal fun BrowserScreen(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    modifier: Modifier = Modifier,
) {
    val ui = rememberBrowserScreenUi()
    // Requests the page makes of the browser: permissions, files, dialogs.
    val pageSession = rememberPageSession(
        activeTabId = state.activeTab?.id,
        isIncognito = state.page.isIncognito,
        permissionGate = sources.permissionGate,
        popups = sources.popups,
    )
    BrowserBackHandlers(state = state.page, webViewActions = hosts.webViewActions)
    HomeBackHandlers(state = state.page, onAction = actions.onAddressBarAction)
    ExternalTabBackHandler(state = state.page, activeTabId = state.activeTab?.id, hooks = hosts.slots.incomingContent)
    // Only whether the find bar is open is read here; the bar collects its own text and count.
    val findOpen by remember(sources.findInPage) {
        sources.findInPage.map { it.isOpen }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val findCallbacks = rememberFindCallbacks(actions.onFindAction, sources.findInPage, hosts.webViewActions)
    // Composed after every other back handler, so Back closes the find bar first.
    BackHandler(enabled = findOpen, onBack = findCallbacks.onClose)
    // The holder also closes the bar on a new page or a tab switch; the highlights go with it.
    LaunchedEffect(findOpen) { if (!findOpen) hosts.webViewActions.clearMatches() }
    IncomingContentEffects(
        hooks = hosts.slots.incomingContent,
        webViewActions = hosts.webViewActions,
        snackbarHostState = hosts.snackbarHostState,
    )
    BrowserScreenEffects(state, ui, hosts.webViewActions)

    val frame = BrowserFrame(ui, findOpen, findCallbacks, pageSession)
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BrowserContent(state, sources, actions, hosts, frame)
        BrowserTopChrome(state, sources, actions, hosts, frame)
        BrowserBottomChrome(state, actions, hosts, frame)
        val bottomInset = with(LocalDensity.current) { ui.bottomBarHeightPx.toDp() }
        SnackbarHost(
            hostState = hosts.snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset),
        )
        // Above everything, bars included.
        FullscreenOverlay(controller = pageSession.fullscreen)
    }
    BrowserHubHost(state, sources, actions, hosts, ui)
    BrowserPageRequests(
        session = pageSession,
        permissionGate = sources.permissionGate,
        longPress = rememberLongPressHooks(state, sources, actions, hosts),
    )
    PopupEffects(popups = sources.popups, snackbarHostState = hosts.snackbarHostState)
}

@Composable
private fun BrowserScreenEffects(state: BrowserScreenState, ui: BrowserScreenUi, webViewActions: WebViewActionsHandle) {
    val activeTab = state.activeTab
    LaunchedEffect(activeTab?.id) {
        // Tab switch: a fresh load on the re-keyed WebView.
        activeTab?.let { webViewActions.loadUrl(it.url) }
    }
    val page = state.page
    LaunchedEffect(page.loadingState, page.isAddressBarFocused) {
        // The bars come back for a new load, an error and while typing.
        if (page.loadingState !is LoadingState.Loaded || page.isAddressBarFocused) ui.chrome.show()
    }
}

/** The find bar or the address bar at the top, and the address suggestions under it. */
@Composable
private fun BoxScope.BrowserTopChrome(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    frame: BrowserFrame,
) {
    val page = state.page
    val ui = frame.ui
    val currentPage by rememberUpdatedState(page)
    val addressBarCallbacks = rememberAddressBarCallbacks(
        onAction = actions.onAddressBarAction,
        onSuggestionsAction = actions.tools.onSuggestionsAction,
        currentUrl = { currentPage.currentUrl },
    )
    FindOrTopBar(
        findOpen = frame.findOpen,
        showTopBar = !page.isHomeContent,
        findBar = {
            FindInPageBar(
                stateFlow = sources.findInPage,
                isIncognito = page.isIncognito,
                callbacks = frame.findCallbacks,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { ui.onTopBarHeight(it.height) },
            )
        },
        topBar = {
            BrowserTopBar(
                state = page,
                addressBarCallbacks = addressBarCallbacks,
                onStarTap = { actions.onPageActionsAction(PageActionsAction.StarTapped) },
                onReloadOrStop = {
                    if (page.loadingState is LoadingState.Loading) {
                        hosts.webViewActions.stopLoading()
                        actions.onPageAction(PageAction.LoadStopped)
                    } else {
                        hosts.webViewActions.reload()
                    }
                },
                progress = hosts.progress,
                focusRequester = ui.addressFocus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { ui.onTopBarHeight(it.height) }
                    .graphicsLayer { translationY = -ui.chrome.hiddenFraction * size.height },
            )
        },
    )
    // Only a focused address bar shows suggestions; on Home the field has its own list.
    BrowserAddressSuggestions(
        visible = page.isAddressBarFocused,
        topBarHeight = with(LocalDensity.current) { ui.topBarHeightPx.toDp() },
        stateFlow = sources.suggestions,
        onPick = { suggestion -> addressBarCallbacks.onSuggestion(suggestion.url) },
    )
}

@Composable
private fun BoxScope.BrowserBottomChrome(
    state: BrowserScreenState,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    frame: BrowserFrame,
) {
    val page = state.page
    val ui = frame.ui
    UnlessFinding(frame.findOpen) {
        BrowserBottomBar(
            tabCount = state.tabCount,
            isIncognito = page.isIncognito,
            isHomeActive = page.isHomeContent,
            callbacks = BrowserBottomBarCallbacks(
                onHome = { actions.onAddressBarAction(AddressBarAction.HomePressed) },
                onSearch = {
                    ui.chrome.show()
                    if (page.isHomeContent) ui.homeSearchFocus.requestFocus() else ui.addressFocus.requestFocus()
                },
                onNewTab = { actions.onPageActionsAction(PageActionsAction.LongPressNewTab) },
                onTabs = hosts.slots.destinations.openTabs,
                onTabsLongPress = { actions.onPageActionsAction(PageActionsAction.LongPressNewTab) },
                onMenu = { ui.hubOpen = true },
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { ui.bottomBarHeightPx = it.height }
                .graphicsLayer { translationY = ui.chrome.hiddenFraction * size.height },
        )
    }
}
