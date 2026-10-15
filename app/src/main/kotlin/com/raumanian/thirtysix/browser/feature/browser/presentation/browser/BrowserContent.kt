@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserErrorState
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomePage
import com.raumanian.thirtysix.browser.feature.browser.presentation.incognito.IncognitoPage
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionList

/** Under the bars: the page, its error state, and Home over it when the tab shows Home. */
@Composable
internal fun BrowserContent(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    frame: BrowserFrame,
) {
    val page = state.page
    val density = LocalDensity.current
    val topInset = with(density) { frame.ui.topBarHeightPx.toDp() }
    val bottomInset = with(density) { frame.ui.bottomBarHeightPx.toDp() }
    TabWebView(state, sources, actions, hosts, frame)
    (page.loadingState as? LoadingState.Failed)?.let { failed ->
        BrowserErrorState(
            reason = failed.reason,
            modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
        )
    }
    if (page.isHomeContent) {
        val homeCallbacks = rememberHomeCallbacks(actions, hosts.webViewActions, hosts.slots)
        HomeOverlay(
            isIncognito = page.isIncognito,
            callbacks = homeCallbacks,
            suggestions = {
                AddressSuggestionList(
                    stateFlow = sources.suggestions,
                    onPick = { suggestion -> homeCallbacks.onSearchSubmit(suggestion.url) },
                )
            },
            searchFocus = frame.ui.homeSearchFocus,
            bottomPadding = bottomInset,
        )
    }
}

/** The always-mounted WebView, keyed by the active tab so a switch disposes the previous one. */
@Composable
private fun TabWebView(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    frame: BrowserFrame,
) {
    val activeTabId = state.activeTab?.id ?: 0L
    val chrome = frame.ui.chrome
    val findOpenNow by rememberUpdatedState(frame.findOpen)
    val onScrollDelta = rememberScrollDeltaHandler(chrome) { findOpenNow }
    val onDownloadRequested = rememberDownloadRequests(actions.onDownloadAction)
    val context = LocalContext.current
    val incognitoProfile = remember(context) { context.incognitoWebProfile() }
    val topBarHeightPx = frame.ui.topBarHeightPx
    val findBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // While finding, the bars stay put, so the page is shortened by the find bar's height:
            // otherwise its end would sit below the screen, unreachable.
            .padding(bottom = findBarPagePadding(frame.findOpen, findBarHeight))
            .graphicsLayer { translationY = topBarHeightPx * (1f - chrome.hiddenFraction) },
    ) {
        key(activeTabId) {
            // A tab a page opened adopts the page's WebView.
            val adopted = remember { state.activeTab?.url?.let(sources.popups::take) }
            BrowserWebView(
                state = state.page,
                actions = hosts.webViewActions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = { actions.onPageAction(PageAction.LoadStarted(it)) },
                    onProgressChanged = { actions.onPageAction(PageAction.ProgressChanged(it)) },
                    onLoadFinished = { actions.onPageAction(PageAction.LoadFinished(it)) },
                    onLoadFailed = { actions.onPageAction(PageAction.LoadFailed(it)) },
                    onDownloadRequested = onDownloadRequested,
                    pageRequests = frame.pageSession,
                    onFindResult = { ordinal, total -> actions.onFindAction(FindInPageAction.Result(ordinal, total)) },
                ),
                navigationCallbacks = pageNavigationCallbacks(actions.onPageAction, onScrollDelta),
                incognitoProfile = incognitoProfile,
                modifier = Modifier.fillMaxSize(),
                adopted = adopted,
                textZoomPercent = state.textZoomPercent,
                desktopMode = remember { sources.desktopMode.isDesktop(activeTabId) },
                protection = sources.protectionHooks,
            )
        }
    }
}

private fun pageNavigationCallbacks(onPageAction: (PageAction) -> Unit, onScrollDelta: (Float) -> Unit) =
    BrowserNavigationCallbacks(
        onUrlChange = { onPageAction(PageAction.UrlChanged(it)) },
        onCanGoBackChange = { onPageAction(PageAction.CanGoBackChanged(it)) },
        onCanGoForwardChange = { onPageAction(PageAction.CanGoForwardChanged(it)) },
        onTitleChange = { onPageAction(PageAction.TitleReceived(it)) },
        onIconReceived = { url, icon -> onPageAction(PageAction.IconReceived(url, icon)) },
        onScreenshotReady = { onPageAction(PageAction.ScreenshotReady(it)) },
        onScrollDelta = onScrollDelta,
    )

/** The Home page in a normal tab, the incognito page in an incognito one; the WebView stays alive underneath. */
@Composable
private fun HomeOverlay(
    isIncognito: Boolean,
    callbacks: HomeCallbacks,
    searchFocus: FocusRequester,
    bottomPadding: Dp,
    suggestions: @Composable () -> Unit,
) {
    if (isIncognito) {
        IncognitoPage(bottomPadding = bottomPadding)
    } else {
        HomePage(
            callbacks = callbacks,
            searchFocus = searchFocus,
            bottomPadding = bottomPadding,
            searchSuggestions = suggestions,
        )
    }
}
