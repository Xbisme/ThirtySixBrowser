@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.AddressBarCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserBottomBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserBottomBarCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserErrorState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.BrowserTopBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubPageToolsState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubSheet
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.PrivacyToolsHosts
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.BrowserPageRequests
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.FullscreenOverlay
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.LongPressActions
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.LongPressHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageRequestSink
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PopupEffects
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PopupTabs
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.SitePermissionGate
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.rememberPageSession
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomePage
import com.raumanian.thirtysix.browser.feature.browser.presentation.incognito.IncognitoPage
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionList
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.BrowserAddressSuggestions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Obtains the browser's state holders and WebView bridges, collects their state once and turns
 * their one-off events into WebView calls and snackbars.
 */
@Composable
@Suppress("LongParameterList")
fun BrowserRoute(
    slots: BrowserSlots,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
    findInPage: FindInPageViewModel = hiltViewModel(),
    hubViewModel: HubViewModel = hiltViewModel(),
    suggestions: AddressSuggestionsViewModel = hiltViewModel(),
    pageTextSize: PageTextSizeViewModel = hiltViewModel(),
    protection: ProtectionViewModel = hiltViewModel(),
    popupTabs: PopupTabs = hiltViewModel(),
    longPressActions: LongPressActions = hiltViewModel(),
    permissionGate: SitePermissionGate = hiltViewModel(),
) {
    val pageState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val state = browserUiState(currentPage, pageState)
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    // Held as State, not delegated: reading `.value` here would recompose on every tick.
    val progress: State<Float> = viewModel.loadProgress.collectAsStateWithLifecycle()
    // The app-wide text size changes rarely; the WebView and the hub read it.
    val textZoomPercent by pageTextSize.textZoomPercent.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }
    val snackbarHostState = remember { SnackbarHostState() }

    BrowserEventEffects(
        events = viewModel.events,
        webViewActions = webViewActions,
        snackbarHostState = snackbarHostState,
    )
    HubEventEffect(events = hubViewModel.events, snackbarHostState = snackbarHostState)
    // After the per-site switch the page reloads with the new setting.
    EventEffect(protection.events) { webViewActions.reload() }

    BrowserScreen(
        state = state,
        tabCount = tabCount,
        activeTab = activeTab,
        progress = { progress.value },
        textZoomPercent = textZoomPercent,
        sources = BrowserScreenSources(
            findInPage = findInPage.state,
            suggestions = suggestions.uiState,
            hasActiveDownloads = hubViewModel.hasActiveDownloads,
            protectionStatus = protection.status,
            protectionDashboard = protection.dashboard,
            protectionHooks = protection.hooks,
            desktopMode = viewModel.desktopMode,
            popups = popupTabs,
            longPressActions = longPressActions,
            permissionGate = permissionGate,
        ),
        actions = BrowserScreenActions(
            onAction = viewModel::onAction,
            onFindAction = findInPage::onAction,
            onHubAction = hubViewModel::onAction,
            onSuggestionsAction = suggestions::onAction,
            onProtectionAction = protection::onAction,
        ),
        webViewActions = webViewActions,
        snackbarHostState = snackbarHostState,
        slots = slots,
        modifier = modifier,
    )
}

/**
 * The browser: the WebView fills the screen and the bars float over it. Scroll offsets and load
 * progress are read only inside `graphicsLayer`/draw lambdas, so scrolling and loading recompose nothing.
 */
@Composable
@Suppress("LongMethod", "LongParameterList") // Top-level host: wires bars, overlays, hub and effects.
internal fun BrowserScreen(
    state: BrowserUiState,
    tabCount: Int,
    activeTab: Tab?,
    progress: () -> Float,
    textZoomPercent: Int,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    webViewActions: WebViewActionsHandle,
    snackbarHostState: SnackbarHostState,
    slots: BrowserSlots,
    modifier: Modifier = Modifier,
) {
    val onAction = actions.onAction
    val currentState by rememberUpdatedState(state)
    val currentTab by rememberUpdatedState(activeTab)
    val addressFocus = remember { FocusRequester() }
    val homeSearchFocus = remember { FocusRequester() }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val chrome = rememberChromeScrollState(maxOffsetPx = topBarHeightPx.toFloat())
    var hubOpen by remember { mutableStateOf(false) }
    var quickSettingRequest by remember { mutableStateOf<QuickSetting?>(null) }
    var quickSettingsUsed by remember { mutableStateOf(false) }

    // Requests the page makes of the browser: permissions, files, dialogs.
    val pageSession = rememberPageSession(
        activeTabId = activeTab?.id,
        isIncognito = state.isIncognito,
        permissionGate = sources.permissionGate,
        popups = sources.popups,
    )
    BrowserBackHandlers(state = state, webViewActions = webViewActions)
    HomeBackHandlers(state = state, onAction = onAction)
    ExternalTabBackHandler(state = state, activeTabId = activeTab?.id, hooks = slots.incomingContent)
    // Only whether the find bar is open is read here; the bar collects its own text and count.
    val findOpen by remember(sources.findInPage) {
        sources.findInPage.map { it.isOpen }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val findCallbacks = rememberFindCallbacks(actions.onFindAction, sources.findInPage, webViewActions)
    var dashboardOpen by rememberSaveable { mutableStateOf(false) }
    val addressBarCallbacks = rememberAddressBarCallbacks(
        onAction = onAction,
        onSuggestionsAction = actions.onSuggestionsAction,
        currentUrl = { currentState.currentUrl },
    )
    // Composed after every other back handler, so Back closes the find bar first.
    BackHandler(enabled = findOpen, onBack = findCallbacks.onClose)
    IncomingContentEffects(
        hooks = slots.incomingContent,
        webViewActions = webViewActions,
        snackbarHostState = snackbarHostState,
    )

    LaunchedEffect(activeTab?.id) {
        // Tab switch: a fresh load on the re-keyed WebView.
        activeTab?.let { webViewActions.loadUrl(it.url) }
    }
    LaunchedEffect(state.loadingState, state.isAddressBarFocused) {
        // The bars come back for a new load, an error and while typing.
        if (state.loadingState !is LoadingState.Loaded || state.isAddressBarFocused) chrome.show()
    }

    val findOpenNow by rememberUpdatedState(findOpen)
    val density = LocalDensity.current
    val topInset = with(density) { topBarHeightPx.toDp() }
    val bottomInset = with(density) { bottomBarHeightPx.toDp() }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BrowserContent(
            state = state,
            actions = actions,
            webViewActions = webViewActions,
            activeTabId = activeTab?.id ?: 0L,
            onScrollDelta = rememberScrollDeltaHandler(chrome) { findOpenNow },
            pageRequests = pageSession,
            takePopup = { activeTab?.url?.let(sources.popups::take) },
            textZoomPercent = textZoomPercent,
            protection = sources.protectionHooks,
            desktopMode = sources.desktopMode,
            modifier = Modifier
                .fillMaxSize()
                // While finding, the bars stay put, so the page is shortened by the find bar's height:
                // otherwise its end would sit below the screen, unreachable.
                .padding(bottom = findBarPagePadding(findOpen, topInset))
                .graphicsLayer { translationY = topBarHeightPx * (1f - chrome.hiddenFraction) },
        )
        (state.loadingState as? LoadingState.Failed)?.let { failed ->
            BrowserErrorState(
                reason = failed.reason,
                modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
            )
        }
        if (state.isHomeContent) {
            val homeCallbacks = rememberHomeCallbacks(actions, webViewActions, slots)
            HomeOverlay(
                isIncognito = state.isIncognito,
                callbacks = homeCallbacks,
                suggestions = {
                    AddressSuggestionList(
                        stateFlow = sources.suggestions,
                        onPick = { suggestion -> homeCallbacks.onSearchSubmit(suggestion.url) },
                    )
                },
                searchFocus = homeSearchFocus,
                bottomPadding = bottomInset,
            )
        }
        FindOrTopBar(
            findOpen = findOpen,
            showTopBar = !state.isHomeContent,
            findBar = {
                FindInPageBar(
                    stateFlow = sources.findInPage,
                    isIncognito = state.isIncognito,
                    callbacks = findCallbacks,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { topBarHeightPx = it.height },
                )
            },
            topBar = {
                BrowserTopBar(
                    state = state,
                    addressBarCallbacks = addressBarCallbacks,
                    onStarTap = { onAction(BrowserAction.StarTapped) },
                    onReloadOrStop = {
                        if (state.loadingState is LoadingState.Loading) {
                            webViewActions.stopLoading()
                            onAction(BrowserAction.Page.LoadStopped)
                        } else {
                            webViewActions.reload()
                        }
                    },
                    progress = progress,
                    focusRequester = addressFocus,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { topBarHeightPx = it.height }
                        .graphicsLayer { translationY = -chrome.hiddenFraction * size.height },
                )
            },
        )
        // Only a focused address bar shows suggestions; on Home the field has its own list.
        BrowserAddressSuggestions(
            visible = state.isAddressBarFocused,
            topBarHeight = topInset,
            stateFlow = sources.suggestions,
            onPick = { suggestion -> addressBarCallbacks.onSuggestion(suggestion.url) },
        )
        UnlessFinding(findOpen) {
            BrowserBottomBar(
                tabCount = tabCount,
                isIncognito = state.isIncognito,
                isHomeActive = state.isHomeContent,
                callbacks = BrowserBottomBarCallbacks(
                    onHome = { onAction(BrowserAction.Home.Pressed) },
                    onSearch = {
                        chrome.show()
                        if (state.isHomeContent) homeSearchFocus.requestFocus() else addressFocus.requestFocus()
                    },
                    onNewTab = { onAction(BrowserAction.LongPressNewTab) },
                    onTabs = slots.destinations.openTabs,
                    onTabsLongPress = { onAction(BrowserAction.LongPressNewTab) },
                    onMenu = { hubOpen = true },
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .graphicsLayer { translationY = chrome.hiddenFraction * size.height },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset),
        )
        // Above everything, bars included.
        FullscreenOverlay(controller = pageSession.fullscreen)
    }

    val pageToolHandler = rememberPageToolHandler(
        onFindAction = actions.onFindAction,
        chrome = chrome,
        actions = PageToolActions(
            share = rememberShareAction(
                currentUrl = { currentState.currentUrl },
                title = { currentTab?.title },
            ),
            toggleDesktopSite = { onAction(BrowserAction.ToggleDesktopMode) },
            // Offered only once the page has loaded.
            print = {
                webViewActions.print(PrintJobName.of(currentTab?.title, currentState.currentUrl))
            },
            openTextSize = {
                quickSettingsUsed = true
                quickSettingRequest = QuickSetting.TextSize
            },
        ),
    )
    if (hubOpen) {
        val available = remember(state.isHomeContent, state.loadingState) {
            PageToolsAvailability.of(state.isHomeContent, state.loadingState)
        }
        BrowserHub(
            state = state,
            hasActiveDownloads = sources.hasActiveDownloads,
            onHubAction = actions.onHubAction,
            webViewActions = webViewActions,
            slots = slots,
            onDismiss = { hubOpen = false },
            onQuickSetting = { setting ->
                quickSettingsUsed = true
                quickSettingRequest = setting
            },
            pageTools = HubPageToolsState(
                available = available,
                isDesktopMode = state.isDesktopMode,
                textSizePercent = textZoomPercent,
            ),
            onPageTool = pageToolHandler,
            // Nothing about protection on Home or the incognito start page.
            protectionFlow = if (state.isHomeContent) null else sources.protectionStatus,
            onProtectionSwitch = { on -> actions.onProtectionAction(ProtectionAction.ProtectionSwitched(on)) },
            onFire = slots.fireButton.onClick,
            onDashboard = { dashboardOpen = true },
        )
    }
    PrivacyToolsHosts(
        dashboardOpen = dashboardOpen,
        isHomeContent = state.isHomeContent,
        onDashboardClose = { dashboardOpen = false },
        dashboardFlow = sources.protectionDashboard,
        onProtectionAction = actions.onProtectionAction,
    )
    slots.fireOverlay()
    if (quickSettingsUsed) {
        slots.quickSettings(quickSettingRequest, { quickSettingRequest = null }, snackbarHostState)
    }
    // Long-press downloads pass the same storage-permission gate as page downloads.
    val context = LocalContext.current
    val longPressDownload = rememberDownloadRequestHandler(
        onGrant = { url, userAgent, disposition, mimeType ->
            onAction(BrowserAction.Download.Requested(url, userAgent, disposition, mimeType))
        },
        onDeny = { permanently -> onAction(BrowserAction.Download.StoragePermissionDenied(permanently)) },
    )
    val currentIncognito by rememberUpdatedState(state.isIncognito)
    val longPress = remember(sources.longPressActions, snackbarHostState, longPressDownload) {
        LongPressHooks(
            actions = sources.longPressActions,
            snackbarHostState = snackbarHostState,
            isIncognito = { currentIncognito },
            onDownload = { url -> longPressDownload(url, WebSettings.getDefaultUserAgent(context), null, null) },
        )
    }
    BrowserPageRequests(session = pageSession, permissionGate = sources.permissionGate, longPress = longPress)
    PopupEffects(popups = sources.popups, snackbarHostState = snackbarHostState)
}

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

@Composable
@Suppress("LongParameterList")
private fun BrowserHub(
    state: BrowserUiState,
    hasActiveDownloads: StateFlow<Boolean>,
    onHubAction: (HubAction) -> Unit,
    webViewActions: WebViewActionsHandle,
    slots: BrowserSlots,
    onDismiss: () -> Unit,
    onQuickSetting: (QuickSetting) -> Unit,
    pageTools: HubPageToolsState,
    onPageTool: (PageTool) -> Unit,
    protectionFlow: StateFlow<PageProtectionStatus>?,
    onProtectionSwitch: (Boolean) -> Unit,
    onFire: () -> Unit,
    onDashboard: () -> Unit,
) {
    HubSheet(
        canGoBack = state.canGoBack,
        canGoForward = state.canGoForward,
        onDismiss = onDismiss,
        hasActiveDownloadsFlow = hasActiveDownloads,
        callbacks = HubCallbacks(
            onSettings = slots.destinations.openSettings,
            onBack = { webViewActions.goBack() },
            onForward = { webViewActions.goForward() },
            onDownloadsInProgress = slots.destinations.openDownloads,
            onPrivacy = slots.destinations.openSettings,
            onNewIncognitoTab = { onHubAction(HubAction.NewIncognitoTab) },
            onDownloads = slots.destinations.openDownloads,
            onHistory = slots.destinations.openHistory,
            onBookmarks = slots.destinations.openBookmarks,
            onQuickSetting = onQuickSetting,
            onPageTool = onPageTool,
            onProtectionSwitched = onProtectionSwitch,
            onFire = onFire,
            onDashboard = onDashboard,
        ),
        pageTools = pageTools,
        protectionFlow = protectionFlow,
    )
}

/** Back history goes to the WebView; a focused address bar consumes Back to clear focus first. */
@Composable
private fun BrowserBackHandlers(
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
private fun HomeBackHandlers(state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    BackHandler(enabled = state.homeOverlay) { onAction(BrowserAction.Home.OverlayDismissed) }
    BackHandler(
        enabled = !state.canGoBack && state.cameFromHome && !state.isIncognito && !state.isHomeContent,
    ) { onAction(BrowserAction.BackWithoutHistory) }
}

@Composable
private fun rememberHomeCallbacks(
    actions: BrowserScreenActions,
    webViewActions: WebViewActionsHandle,
    slots: BrowserSlots,
): HomeCallbacks = remember(actions, webViewActions, slots) {
    HomeCallbacks(
        onSearchSubmit = { input -> actions.onAction(BrowserAction.Home.Submit(input)) },
        onOpenUrl = { url ->
            actions.onAction(BrowserAction.Home.NavigateFrom)
            webViewActions.loadUrl(url)
        },
        onSeeAllHistory = slots.destinations.openHistory,
        onNewIncognitoTab = { actions.onHubAction(HubAction.NewIncognitoTab) },
        // Home's field starts empty, so there is no page address to compare with.
        onSearchTextChange = { text ->
            actions.onSuggestionsAction(AddressSuggestionsAction.TextChanged(text, currentPageUrl = null))
        },
        onSearchFocusChange = { focused ->
            actions.onSuggestionsAction(AddressSuggestionsAction.FocusChanged(focused))
        },
    )
}

/** The always-mounted WebView, keyed by the active tab so a switch disposes the previous one. */
@Composable
@Suppress("LongParameterList")
private fun BrowserContent(
    state: BrowserUiState,
    actions: BrowserScreenActions,
    webViewActions: WebViewActionsHandle,
    activeTabId: Long,
    onScrollDelta: (Float) -> Unit,
    pageRequests: PageRequestSink,
    takePopup: () -> WebView?,
    textZoomPercent: Int,
    protection: ProtectionHooks,
    desktopMode: DesktopModeLookup,
    modifier: Modifier = Modifier,
) {
    // On API 29+ the storage-permission gate passes straight through.
    val onDownloadRequested = rememberDownloadRequestHandler(
        onGrant = { url, userAgent, disposition, mimeType ->
            actions.onAction(BrowserAction.Download.Requested(url, userAgent, disposition, mimeType))
        },
        onDeny = { permanently -> actions.onAction(BrowserAction.Download.StoragePermissionDenied(permanently)) },
    )
    val context = LocalContext.current
    val incognitoProfile = remember(context) { context.incognitoWebProfile() }
    Box(modifier = modifier) {
        androidx.compose.runtime.key(activeTabId) {
            // A tab a page opened adopts the page's WebView.
            val adopted = remember { takePopup() }
            BrowserWebView(
                state = state,
                actions = webViewActions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = { actions.onAction(BrowserAction.Page.LoadStarted(it)) },
                    onProgressChanged = { actions.onAction(BrowserAction.Page.ProgressChanged(it)) },
                    onLoadFinished = { actions.onAction(BrowserAction.Page.LoadFinished(it)) },
                    onLoadFailed = { actions.onAction(BrowserAction.Page.LoadFailed(it)) },
                    onDownloadRequested = onDownloadRequested,
                    pageRequests = pageRequests,
                    onFindResult = { ordinal, total -> actions.onFindAction(FindInPageAction.Result(ordinal, total)) },
                ),
                navigationCallbacks = BrowserNavigationCallbacks(
                    onUrlChange = { actions.onAction(BrowserAction.Page.UrlChanged(it)) },
                    onCanGoBackChange = { actions.onAction(BrowserAction.Page.CanGoBackChanged(it)) },
                    onCanGoForwardChange = { actions.onAction(BrowserAction.Page.CanGoForwardChanged(it)) },
                    onTitleChange = { actions.onAction(BrowserAction.Page.TitleReceived(it)) },
                    onIconReceived = { url, icon -> actions.onAction(BrowserAction.Page.IconReceived(url, icon)) },
                    onScreenshotReady = { actions.onAction(BrowserAction.Page.ScreenshotReady(it)) },
                    onScrollDelta = onScrollDelta,
                ),
                incognitoProfile = incognitoProfile,
                modifier = Modifier.fillMaxSize(),
                adopted = adopted,
                textZoomPercent = textZoomPercent,
                desktopMode = remember { desktopMode.isDesktop(activeTabId) },
                protection = protection,
            )
        }
    }
}

/** A submit reaches the view model, whose load event dismisses the keyboard and clears focus first. */
@Composable
private fun rememberAddressBarCallbacks(
    onAction: (BrowserAction) -> Unit,
    onSuggestionsAction: (AddressSuggestionsAction) -> Unit,
    currentUrl: () -> String,
): AddressBarCallbacks {
    val pageUrl by rememberUpdatedState(currentUrl)
    return remember(onAction, onSuggestionsAction) {
        val submit = { onAction(BrowserAction.AddressBar.Submit) }
        AddressBarCallbacks(
            onTextChange = { text ->
                onAction(BrowserAction.AddressBar.TextChange(text))
                onSuggestionsAction(AddressSuggestionsAction.TextChanged(text, currentPageUrl = pageUrl()))
            },
            onFocusChange = { focused ->
                onAction(BrowserAction.AddressBar.FocusChange(focused))
                onSuggestionsAction(AddressSuggestionsAction.FocusChanged(focused))
            },
            onSubmit = submit,
            onClear = { onAction(BrowserAction.AddressBar.Clear) },
            // A chosen suggestion is submitted exactly like a typed address.
            onSuggestion = { url ->
                onAction(BrowserAction.AddressBar.TextChange(url))
                submit()
            },
        )
    }
}
