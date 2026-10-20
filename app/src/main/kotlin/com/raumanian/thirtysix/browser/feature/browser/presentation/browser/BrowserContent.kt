@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserErrorState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TabSwipePreview
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomePage
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeSearchBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeSearchHooks
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
    val tabSwipe = frame.ui.tabSwipe
    TabSwipeHoldEffect(tabSwipe, state.activeTab?.id)
    // One emitter: the incoming tab's preview sits over the page and Home alike.
    Box(modifier = Modifier.fillMaxSize()) {
        TabWebView(state, sources, actions, hosts, frame)
        (page.loadingState as? LoadingState.Failed)?.let { failed ->
            BrowserErrorState(
                reason = failed.reason,
                modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
            )
        }
        if (page.isHomeContent) {
            val searchSwipe = rememberHomeSearchSwipe(state, actions, frame)
            val baseCallbacks = rememberHomeCallbacks(actions, hosts.webViewActions, hosts.slots)
            val homeCallbacks = remember(baseCallbacks, searchSwipe) {
                baseCallbacks.copy(searchFieldModifier = searchSwipe.lane.then(searchSwipe.field))
            }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = tabSwipe.fraction * size.width }) {
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
        val screenshotVersion = sources.screenshotVersion.collectAsStateWithLifecycle()
        TabSwipePreview(
            state = tabSwipe,
            screenshots = sources.tabScreenshots,
            screenshotVersion = { screenshotVersion.value },
            topInset = topInset,
        )
    }
}

/** After a swipe commits, the incoming preview stays until the new tab's page is on screen. */
@Composable
private fun TabSwipeHoldEffect(tabSwipe: TabSwipeState, activeTabId: Long?) {
    LaunchedEffect(activeTabId) {
        if (tabSwipe.holding) tabSwipe.awaitPageThenRelease(waitForPage = tabSwipe.incoming?.isHome != true)
    }
}

/** On Home and the incognito start page, their search field takes the address pill's swipe and actions. */
@Composable
private fun rememberHomeSearchSwipe(
    state: BrowserScreenState,
    actions: BrowserScreenActions,
    frame: BrowserFrame,
): FieldSwipe {
    var focused by remember { mutableStateOf(false) }
    val swipe = rememberFieldSwipe(frame.ui.tabSwipe, state.tabSwipe, !focused, actions.tools.onTabSwipeAction)
    return remember(swipe) {
        FieldSwipe(lane = Modifier.onFocusChanged { focused = it.isFocused }.then(swipe.lane), field = swipe.field)
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
    val chrome = frame.ui.chrome
    val topBarHeightPx = frame.ui.topBarHeightPx
    val findBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // While finding, the bars stay put, so the page is shortened by the find bar's height:
            // otherwise its end would sit below the screen, unreachable.
            .padding(bottom = findBarPagePadding(frame.findOpen, findBarHeight))
            .graphicsLayer {
                translationY = topBarHeightPx * (1f - chrome.hiddenFraction)
                translationX = frame.ui.tabSwipe.fraction * size.width
            },
    ) {
        // Built only once the tab's saved back and forward has been read, so it can be restored first.
        if (state.isTabReady) {
            key(state.activeTab?.id ?: 0L) {
                TabPage(state, sources, actions, hosts, frame)
            }
        }
        SwipeNavigationIndicator(frame.ui.swipe)
    }
}

@Composable
private fun TabPage(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    frame: BrowserFrame,
) {
    val activeTabId = state.activeTab?.id ?: 0L
    val findOpenNow by rememberUpdatedState(frame.findOpen)
    val onScrollDelta = rememberScrollDeltaHandler(frame.ui.chrome) { findOpenNow }
    val onDownloadRequested = rememberDownloadRequests(actions.onDownloadAction)
    val context = LocalContext.current
    val incognitoProfile = remember(context) { context.incognitoWebProfile() }
    // A tab a page opened adopts the page's WebView.
    val adopted = remember { state.activeTab?.url?.let(sources.popups::take) }
    val session = remember {
        state.activeTab?.let { TabSessionHooks(it.id, sources.savedState, actions.onTabSessionAction) }
    }
    val swipe = rememberSwipeNavigation(state, frame)
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
            onPageVisible = frame.ui.tabSwipe::onPageVisible,
        ),
        navigationCallbacks = pageNavigationCallbacks(actions.onPageAction, onScrollDelta, state.activeTab),
        incognitoProfile = incognitoProfile,
        modifier = Modifier.fillMaxSize(),
        adopted = adopted,
        textZoomPercent = state.textZoomPercent,
        desktopMode = remember { sources.desktopMode.isDesktop(activeTabId) },
        protection = sources.protectionHooks,
        session = session,
        swipe = swipe,
    )
}

/** Swiping is off on Home and the incognito start page, and while the address bar or the find bar is in use. */
@Composable
private fun rememberSwipeNavigation(state: BrowserScreenState, frame: BrowserFrame): SwipeNavigationHooks {
    val page = state.page
    val enabled by rememberUpdatedState(
        state.swipeNavigationEnabled && !page.isHomeContent && !page.isAddressBarFocused && !frame.findOpen,
    )
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val thresholdPx = with(LocalDensity.current) { Dimens.swipeNavigationThreshold.toPx() }
    val swipeState = frame.ui.swipe
    return remember(swipeState, thresholdPx) { SwipeNavigationHooks(swipeState, { enabled }, { rtl }, thresholdPx) }
}

// The WebView keeps the callbacks of its first composition, so results name the tab it was built for.
private fun pageNavigationCallbacks(onPageAction: (PageAction) -> Unit, onScrollDelta: (Float) -> Unit, tab: Tab?) =
    BrowserNavigationCallbacks(
        onUrlChange = { onPageAction(PageAction.UrlChanged(it)) },
        onCanGoBackChange = { onPageAction(PageAction.CanGoBackChanged(it)) },
        onCanGoForwardChange = { onPageAction(PageAction.CanGoForwardChanged(it)) },
        onTitleChange = { onPageAction(PageAction.TitleReceived(it)) },
        onIconReceived = { url, icon -> onPageAction(PageAction.IconReceived(url, icon, tab?.isIncognito ?: false)) },
        onScreenshotReady = { bitmap ->
            tab?.let { onPageAction(PageAction.ScreenshotReady(bitmap, it.id, it.isIncognito)) }
        },
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
        // No address suggestions here: they come from normal browsing.
        IncognitoPage(bottomPadding = bottomPadding) {
            HomeSearchBar(
                engine = null,
                onSubmit = callbacks.onSearchSubmit,
                focusRequester = searchFocus,
                hooks = HomeSearchHooks(fieldModifier = callbacks.searchFieldModifier),
            )
        }
    } else {
        HomePage(
            callbacks = callbacks,
            searchFocus = searchFocus,
            bottomPadding = bottomPadding,
            searchSuggestions = suggestions,
        )
    }
}
