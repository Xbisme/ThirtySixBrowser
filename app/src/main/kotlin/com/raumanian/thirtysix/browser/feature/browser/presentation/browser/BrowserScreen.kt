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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
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
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.rememberPageSession
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomeCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.home.HomePage
import com.raumanian.thirtysix.browser.feature.browser.presentation.incognito.IncognitoPage
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionList
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsViewModel
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.BrowserAddressSuggestions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The browser: the WebView fills the screen and the bars float over it. Scroll offsets and load
 * progress are read only inside `graphicsLayer`/draw lambdas, so scrolling and loading recompose nothing.
 */
@Composable
@Suppress("LongMethod") // Top-level host: wires bars, overlays, hub, effects and snackbars.
fun BrowserScreen(
    slots: BrowserSlots,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
    hubViewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    // Held as State, not delegated: reading `.value` here would recompose on every tick.
    val progress: State<Float> = viewModel.loadProgress.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }
    val snackbarHostState = remember { SnackbarHostState() }
    val addressFocus = remember { FocusRequester() }
    val homeSearchFocus = remember { FocusRequester() }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val chrome = rememberChromeScrollState(maxOffsetPx = topBarHeightPx.toFloat())
    var hubOpen by remember { mutableStateOf(false) }
    var quickSettingRequest by remember { mutableStateOf<QuickSetting?>(null) }
    var quickSettingsUsed by remember { mutableStateOf(false) }

    // Requests the page makes of the browser: permissions, files, dialogs.
    val popupTabs: PopupTabs = hiltViewModel()
    val pageSession = rememberPageSession(
        activeTabId = activeTab?.id,
        isIncognito = state.isIncognito,
        popups = popupTabs,
    )
    BrowserBackHandlers(state = state, webViewActions = webViewActions)
    HomeBackHandlers(state = state, viewModel = viewModel)
    ExternalTabBackHandler(state = state, activeTabId = activeTab?.id, hooks = slots.incomingContent)
    // Only whether the find bar is open is read here; the bar collects its own text and count.
    val findOpen by remember(viewModel) {
        viewModel.findInPage.map { it.isOpen }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val findCallbacks = rememberFindCallbacks(viewModel, webViewActions)
    val suggestions: AddressSuggestionsViewModel = hiltViewModel()
    // The app-wide text size changes rarely; the WebView and the hub read it.
    val pageTextSize: PageTextSizeViewModel = hiltViewModel()
    val textZoomPercent by pageTextSize.textZoomPercent.collectAsStateWithLifecycle()
    val protection: ProtectionViewModel = hiltViewModel()
    var dashboardOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(protection) {
        // After the per-site switch the page reloads with the new setting.
        protection.reloadEvents.collect { webViewActions.reload() }
    }
    val addressBarCallbacks = rememberAddressBarCallbacks(viewModel, webViewActions, suggestions)
    // Composed after every other back handler, so Back closes the find bar first.
    BackHandler(enabled = findOpen, onBack = findCallbacks.onClose)
    BrowserSnackbarEffects(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
    IncomingContentEffects(
        hooks = slots.incomingContent,
        webViewActions = webViewActions,
        snackbarHostState = snackbarHostState,
    )
    HubEventEffect(hubViewModel = hubViewModel, snackbarHostState = snackbarHostState)

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
            viewModel = viewModel,
            webViewActions = webViewActions,
            activeTabId = activeTab?.id ?: 0L,
            onScrollDelta = rememberScrollDeltaHandler(chrome) { findOpenNow },
            pageRequests = pageSession,
            takePopup = { activeTab?.url?.let(popupTabs::take) },
            textZoomPercent = textZoomPercent,
            protection = protection.hooks,
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
            val homeCallbacks =
                rememberHomeCallbacks(viewModel, hubViewModel, webViewActions, slots, suggestions)
            HomeOverlay(
                isIncognito = state.isIncognito,
                callbacks = homeCallbacks,
                suggestions = {
                    AddressSuggestionList(
                        stateFlow = suggestions.uiState,
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
                    stateFlow = viewModel.findInPage,
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
                    onStarTap = viewModel::onStarTapped,
                    onReloadOrStop = {
                        if (state.loadingState is LoadingState.Loading) {
                            webViewActions.stopLoading()
                            viewModel.onLoadStopped()
                        } else {
                            webViewActions.reload()
                        }
                    },
                    progress = { progress.value },
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
            stateFlow = suggestions.uiState,
            onPick = { suggestion -> addressBarCallbacks.onSuggestion(suggestion.url) },
        )
        UnlessFinding(findOpen) {
            BrowserBottomBar(
                tabCount = tabCount,
                isIncognito = state.isIncognito,
                isHomeActive = state.isHomeContent,
                callbacks = BrowserBottomBarCallbacks(
                    onHome = viewModel::onHomePressed,
                    onSearch = {
                        chrome.show()
                        if (state.isHomeContent) homeSearchFocus.requestFocus() else addressFocus.requestFocus()
                    },
                    onNewTab = viewModel::onLongPressNewTab,
                    onTabs = slots.destinations.openTabs,
                    onTabsLongPress = viewModel::onLongPressNewTab,
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
        viewModel = viewModel,
        chrome = chrome,
        actions = PageToolActions(
            share = rememberShareAction(viewModel),
            toggleDesktopSite = { viewModel.onDesktopModeToggled()?.let(webViewActions.setDesktopMode) },
            // Offered only once the page has loaded.
            print = {
                val title = viewModel.activeTab.value?.title
                webViewActions.print(PrintJobName.of(title, viewModel.uiState.value.currentUrl))
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
            hubViewModel = hubViewModel,
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
            protectionFlow = if (state.isHomeContent) null else protection.status,
            onProtectionSwitch = protection::onProtectionSwitched,
            onFire = slots.fireButton.onClick,
            onDashboard = { dashboardOpen = true },
        )
    }
    PrivacyToolsHosts(
        dashboardOpen = dashboardOpen,
        isHomeContent = state.isHomeContent,
        onDashboardClose = { dashboardOpen = false },
        protection = protection,
    )
    slots.fireOverlay()
    if (quickSettingsUsed) {
        slots.quickSettings(quickSettingRequest, { quickSettingRequest = null }, snackbarHostState)
    }
    // Long-press downloads pass the same storage-permission gate as page downloads.
    val context = LocalContext.current
    val longPressDownload = rememberDownloadRequestHandler(
        onGrant = viewModel::onDownloadRequested,
        onDeny = viewModel::onStoragePermissionDenied,
    )
    val longPressActions: LongPressActions = hiltViewModel()
    val currentIncognito by rememberUpdatedState(state.isIncognito)
    val longPress = remember(longPressActions, snackbarHostState, longPressDownload) {
        LongPressHooks(
            actions = longPressActions,
            snackbarHostState = snackbarHostState,
            isIncognito = { currentIncognito },
            onDownload = { url -> longPressDownload(url, WebSettings.getDefaultUserAgent(context), null, null) },
        )
    }
    BrowserPageRequests(session = pageSession, longPress = longPress)
    PopupEffects(popups = popupTabs, snackbarHostState = snackbarHostState)
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
    hubViewModel: HubViewModel,
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
        hasActiveDownloadsFlow = hubViewModel.hasActiveDownloads,
        callbacks = HubCallbacks(
            onSettings = slots.destinations.openSettings,
            onBack = { webViewActions.goBack() },
            onForward = { webViewActions.goForward() },
            onDownloadsInProgress = slots.destinations.openDownloads,
            onPrivacy = slots.destinations.openSettings,
            onNewIncognitoTab = hubViewModel::onNewIncognitoTab,
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
private fun HomeBackHandlers(state: BrowserUiState, viewModel: BrowserViewModel) {
    BackHandler(enabled = state.homeOverlay) { viewModel.onHomeOverlayDismissed() }
    BackHandler(
        enabled = !state.canGoBack && state.cameFromHome && !state.isIncognito && !state.isHomeContent,
    ) { viewModel.onBackWithoutHistory() }
}

@Composable
private fun rememberHomeCallbacks(
    viewModel: BrowserViewModel,
    hubViewModel: HubViewModel,
    webViewActions: WebViewActionsHandle,
    slots: BrowserSlots,
    suggestions: AddressSuggestionsViewModel,
): HomeCallbacks {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(
        viewModel,
        hubViewModel,
        webViewActions,
        slots,
        keyboardController,
        focusManager,
        suggestions,
    ) {
        HomeCallbacks(
            onSearchSubmit = { input ->
                viewModel.onHomeSubmit(input) { url ->
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    webViewActions.loadUrl(url)
                }
            },
            onOpenUrl = { url ->
                viewModel.onNavigateFromHome()
                webViewActions.loadUrl(url)
            },
            onSeeAllHistory = slots.destinations.openHistory,
            onNewIncognitoTab = hubViewModel::onNewIncognitoTab,
            // Home's field starts empty, so there is no page address to compare with.
            onSearchTextChange = { text -> suggestions.onTextChanged(text, currentPageUrl = null) },
            onSearchFocusChange = { focused -> suggestions.onFocusChanged(focused) },
        )
    }
}

@Composable
private fun BrowserSnackbarEffects(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val maxTabsMessage = stringResource(R.string.browser_max_tabs_reached)
    val bookmarkAddedMessage = stringResource(R.string.bookmark_added_snackbar)
    val bookmarkRemovedMessage = stringResource(R.string.bookmark_removed_snackbar)
    val downloadStartedTemplate = stringResource(R.string.downloads_started_snackbar)
    val downloadServiceUnavailableMessage = stringResource(R.string.downloads_service_unavailable)
    val storagePermissionDeniedMessage = stringResource(R.string.downloads_storage_permission_denied)
    val storagePermissionSettingsMessage = stringResource(R.string.downloads_storage_permission_denied_permanently)

    LaunchedEffect(state.tabsEvent) {
        if (state.tabsEvent is TabsErrorEvent.MaxTabsReached) {
            snackbarHostState.showSnackbar(maxTabsMessage)
            viewModel.consumeTabsEvent()
        }
    }
    LaunchedEffect(state.bookmarkSnackbarEvent) {
        val message = when (state.bookmarkSnackbarEvent) {
            BookmarkSnackbarEvent.Added -> bookmarkAddedMessage
            BookmarkSnackbarEvent.Removed -> bookmarkRemovedMessage
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeBookmarkSnackbarEvent()
        }
    }
    LaunchedEffect(state.downloadSnackbarEvent) {
        val message = when (val event = state.downloadSnackbarEvent) {
            is DownloadSnackbarEvent.Started -> downloadStartedTemplate.format(event.fileName)
            DownloadSnackbarEvent.ServiceUnavailable -> downloadServiceUnavailableMessage
            DownloadSnackbarEvent.StoragePermissionDenied -> storagePermissionDeniedMessage
            DownloadSnackbarEvent.StoragePermissionPermanentlyDenied -> storagePermissionSettingsMessage
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeDownloadSnackbarEvent()
        }
    }
}

/** Snackbars for hub actions that finish after the sheet has closed. */
@Composable
private fun HubEventEffect(hubViewModel: HubViewModel, snackbarHostState: SnackbarHostState) {
    val maxIncognitoMessage = stringResource(R.string.tabs_error_max_incognito_tabs_reached)
    LaunchedEffect(hubViewModel) {
        hubViewModel.events.collect { event ->
            when (event) {
                HubEvent.MaxIncognitoTabsReached -> snackbarHostState.showSnackbar(maxIncognitoMessage)
            }
        }
    }
}

/** The always-mounted WebView, keyed by the active tab so a switch disposes the previous one. */
@Composable
@Suppress("LongParameterList")
private fun BrowserContent(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
    activeTabId: Long,
    onScrollDelta: (Float) -> Unit,
    pageRequests: PageRequestSink,
    takePopup: () -> WebView?,
    textZoomPercent: Int,
    protection: ProtectionHooks,
    modifier: Modifier = Modifier,
) {
    // On API 29+ the storage-permission gate passes straight through.
    val onDownloadRequested = rememberDownloadRequestHandler(
        onGrant = viewModel::onDownloadRequested,
        onDeny = viewModel::onStoragePermissionDenied,
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
                    onLoadStarted = viewModel::onLoadStarted,
                    onProgressChanged = viewModel::onProgressChanged,
                    onLoadFinished = viewModel::onLoadFinished,
                    onLoadFailed = viewModel::onLoadFailed,
                    onDownloadRequested = onDownloadRequested,
                    pageRequests = pageRequests,
                    onFindResult = viewModel::onFindResult,
                ),
                navigationCallbacks = BrowserNavigationCallbacks(
                    onUrlChange = viewModel::onUrlChanged,
                    onCanGoBackChange = viewModel::onCanGoBackChanged,
                    onCanGoForwardChange = viewModel::onCanGoForwardChanged,
                    onTitleChange = viewModel::onTitleReceived,
                    onIconReceived = viewModel::onIconReceived,
                    onScreenshotReady = viewModel::onScreenshotReady,
                    onScrollDelta = onScrollDelta,
                ),
                incognitoProfile = incognitoProfile,
                modifier = Modifier.fillMaxSize(),
                adopted = adopted,
                textZoomPercent = textZoomPercent,
                desktopMode = remember { viewModel.isDesktopMode(activeTabId) },
                protection = protection,
            )
        }
    }
}

/** On a non-empty submit: dismiss the keyboard, clear focus, then load, in that order. */
@Composable
private fun rememberAddressBarCallbacks(
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
    suggestions: AddressSuggestionsViewModel,
): AddressBarCallbacks {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(viewModel, webViewActions, keyboardController, focusManager, suggestions) {
        val submit = {
            viewModel.onAddressBarSubmit { url ->
                keyboardController?.hide()
                focusManager.clearFocus()
                webViewActions.loadUrl(url)
            }
            Unit
        }
        AddressBarCallbacks(
            onTextChange = { text ->
                viewModel.onAddressBarTextChange(text)
                suggestions.onTextChanged(text, currentPageUrl = viewModel.uiState.value.currentUrl)
            },
            onFocusChange = { focused ->
                viewModel.onAddressBarFocusChange(focused)
                suggestions.onFocusChanged(focused)
            },
            onSubmit = submit,
            onClear = viewModel::onAddressBarClear,
            // A chosen suggestion is submitted exactly like a typed address.
            onSuggestion = { url ->
                viewModel.onAddressBarTextChange(url)
                submit()
            },
        )
    }
}
