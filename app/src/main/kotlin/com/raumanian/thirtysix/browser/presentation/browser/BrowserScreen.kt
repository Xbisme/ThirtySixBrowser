@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBarCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserBottomBar
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserBottomBarCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserErrorState
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserTopBar
import com.raumanian.thirtysix.browser.presentation.browser.components.HubCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.HubSheet
import com.raumanian.thirtysix.browser.presentation.browser.components.QuickSetting
import com.raumanian.thirtysix.browser.presentation.browser.page.BrowserPageRequests
import com.raumanian.thirtysix.browser.presentation.browser.page.FullscreenOverlay
import com.raumanian.thirtysix.browser.presentation.browser.page.LongPressActions
import com.raumanian.thirtysix.browser.presentation.browser.page.LongPressHooks
import com.raumanian.thirtysix.browser.presentation.browser.page.PageRequestSink
import com.raumanian.thirtysix.browser.presentation.browser.page.PopupEffects
import com.raumanian.thirtysix.browser.presentation.browser.page.PopupTabs
import com.raumanian.thirtysix.browser.presentation.browser.page.rememberPageSession
import com.raumanian.thirtysix.browser.presentation.entry.IncomingContentHooks
import com.raumanian.thirtysix.browser.presentation.home.HomeCallbacks
import com.raumanian.thirtysix.browser.presentation.home.HomePage
import com.raumanian.thirtysix.browser.presentation.incognito.IncognitoPage
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.tabs.TabsErrorEvent

/**
 * Spec 007–018 browser screen; Spec 019 rebuilt its chrome (research R7, R8, R12, R16).
 *
 * Layout: the WebView fills the screen; the top bar and the bottom bar float over it and each
 * pads itself by its system-bar inset (FR-008). As the page scrolls, [ChromeScrollState] slides
 * both bars away and shifts the WebView up to meet them. **Every one of those movements is read
 * inside a `graphicsLayer { }` lambda**, so a scroll frame redraws layers and recomposes nothing
 * (Constitution v1.4.0 §V rule 1).
 *
 * Page-load progress comes from [BrowserViewModel.loadProgress], collected into a [State] that
 * is **held, never delegated with `by`**, and handed down as `{ progress.value }` — so a progress
 * tick reaches only the progress line's draw phase (§V rules 2–3, research R8).
 *
 * Menu opens the hub ([HubSheet], FR-030); its quick-setting tiles open Spec 016's choosers via
 * [QuickSettingsHost]. Back is the system gesture (FR-021); there is no Back button on the bar.
 */
@Composable
@Suppress("LongMethod") // Top-level host: wires bars, overlays, hub, effects and snackbars.
fun BrowserScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: BrowserViewModel = hiltViewModel(),
    hubViewModel: HubViewModel = hiltViewModel(),
    incomingContent: IncomingContentHooks = IncomingContentHooks(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    // Held as State, NOT delegated: reading `.value` here would recompose on every tick.
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

    // Spec 021 — requests the page makes of the browser (permissions, files, dialogs, …).
    val popupTabs: PopupTabs = hiltViewModel()
    val pageSession = rememberPageSession(
        activeTabId = activeTab?.id,
        isIncognito = state.isIncognito,
        popups = popupTabs,
    )
    BrowserBackHandlers(state = state, webViewActions = webViewActions)
    HomeBackHandlers(state = state, viewModel = viewModel)
    ExternalTabBackHandler(state = state, activeTabId = activeTab?.id, hooks = incomingContent)
    BrowserSnackbarEffects(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
    IncomingContentEffects(
        hooks = incomingContent,
        webViewActions = webViewActions,
        snackbarHostState = snackbarHostState,
    )
    HubEventEffect(hubViewModel = hubViewModel, snackbarHostState = snackbarHostState)

    LaunchedEffect(activeTab?.id) {
        // Spec 011 — tab-switch driver: a fresh load on the key-recreated WebView.
        activeTab?.let { webViewActions.loadUrl(it.url) }
    }
    LaunchedEffect(state.loadingState, state.isAddressBarFocused) {
        // FR-025 — the bars come back for a new load, an error and while typing.
        if (state.loadingState !is LoadingState.Loaded || state.isAddressBarFocused) chrome.show()
    }

    val density = LocalDensity.current
    val topInset = with(density) { topBarHeightPx.toDp() }
    val bottomInset = with(density) { bottomBarHeightPx.toDp() }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BrowserContent(
            state = state,
            viewModel = viewModel,
            webViewActions = webViewActions,
            activeTabId = activeTab?.id ?: 0L,
            onScrollDelta = chrome::onScrollDelta,
            pageRequests = pageSession,
            takePopup = { activeTab?.url?.let(popupTabs::take) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = topBarHeightPx * (1f - chrome.hiddenFraction) },
        )
        (state.loadingState as? LoadingState.Failed)?.let { failed ->
            BrowserErrorState(
                reason = failed.reason,
                modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
            )
        }
        if (state.isHomeContent) {
            HomeOverlay(
                isIncognito = state.isIncognito,
                callbacks = rememberHomeCallbacks(viewModel, hubViewModel, webViewActions, navController),
                searchFocus = homeSearchFocus,
                bottomPadding = bottomInset,
            )
        }
        if (!state.isHomeContent) {
            BrowserTopBar(
                state = state,
                addressBarCallbacks = rememberAddressBarCallbacks(viewModel, webViewActions),
                onStarTapped = viewModel::onStarTapped,
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
        }
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
                onTabs = { navController.navigate(AppDestination.Tabs.route) },
                onTabsLongPress = viewModel::onLongPressNewTab,
                onMenu = { hubOpen = true },
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .graphicsLayer { translationY = chrome.hiddenFraction * size.height },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset),
        )
        // Spec 021 US3 — above everything, bars included.
        FullscreenOverlay(controller = pageSession.fullscreen)
    }

    if (hubOpen) {
        BrowserHub(
            state = state,
            hubViewModel = hubViewModel,
            webViewActions = webViewActions,
            navController = navController,
            onDismiss = { hubOpen = false },
            onQuickSetting = { setting ->
                quickSettingsUsed = true
                quickSettingRequest = setting
            },
        )
    }
    if (quickSettingsUsed) {
        QuickSettingsHost(
            request = quickSettingRequest,
            onRequestHandled = { quickSettingRequest = null },
            snackbarHostState = snackbarHostState,
        )
    }
    // Spec 021 US4 — long-press downloads pass the same storage-permission gate as page downloads.
    val context = LocalContext.current
    val longPressDownload = rememberDownloadRequestHandler(
        onGranted = viewModel::onDownloadRequested,
        onDenied = viewModel::onStoragePermissionDenied,
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

/**
 * Spec 019 FR-010 / FR-015 — the Home page in a normal tab, the incognito page in an incognito
 * one. The WebView stays alive underneath (research R1).
 */
@Composable
private fun HomeOverlay(
    isIncognito: Boolean,
    callbacks: HomeCallbacks,
    searchFocus: FocusRequester,
    bottomPadding: Dp,
) {
    if (isIncognito) {
        IncognitoPage(bottomPadding = bottomPadding)
    } else {
        HomePage(callbacks = callbacks, searchFocus = searchFocus, bottomPadding = bottomPadding)
    }
}

/** Spec 019 FR-030 — the hub sheet, wired to navigation, the WebView and the quick settings. */
@Composable
@Suppress("LongParameterList")
private fun BrowserHub(
    state: BrowserUiState,
    hubViewModel: HubViewModel,
    webViewActions: WebViewActionsHandle,
    navController: NavHostController,
    onDismiss: () -> Unit,
    onQuickSetting: (QuickSetting) -> Unit,
) {
    HubSheet(
        canGoBack = state.canGoBack,
        canGoForward = state.canGoForward,
        onDismiss = onDismiss,
        hasActiveDownloadsFlow = hubViewModel.hasActiveDownloads,
        callbacks = HubCallbacks(
            onSettings = { navController.navigate(AppDestination.Settings.route) },
            onBack = { webViewActions.goBack() },
            onForward = { webViewActions.goForward() },
            onDownloadsInProgress = { navController.navigate(AppDestination.Downloads.route) },
            onPrivacy = { navController.navigate(AppDestination.Settings.route) },
            onNewIncognitoTab = hubViewModel::onNewIncognitoTab,
            onDownloads = { navController.navigate(AppDestination.Downloads.route) },
            onHistory = { navController.navigate(AppDestination.History.route) },
            onBookmarks = { navController.navigate(AppDestination.Bookmarks.route) },
            onQuickSetting = onQuickSetting,
        ),
    )
}

/**
 * Spec 008 + Spec 009 — system-back integration. [PredictiveBackHandler] is
 * enabled only when the WebView has back history; the in-screen [BackHandler]
 * ahead of it consumes the back gesture iff the address bar is focused
 * (clears focus + dismisses keyboard).
 */
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
 * Spec 019 (data-model §2) — Back over the Home overlay returns to the page underneath; Back on
 * a page opened from Home, with no page history left, returns to Home. Composed after
 * [BrowserBackHandlers] so these take precedence when enabled.
 */
@Composable
private fun HomeBackHandlers(state: BrowserUiState, viewModel: BrowserViewModel) {
    BackHandler(enabled = state.homeOverlay) { viewModel.onHomeOverlayDismissed() }
    BackHandler(
        enabled = !state.canGoBack && state.cameFromHome && !state.isIncognito && !state.isHomeContent,
    ) { viewModel.onBackWithoutHistory() }
}

/** Spec 019 contracts C1/C4 — what the Home page's search bar, cards and rows do. */
@Composable
private fun rememberHomeCallbacks(
    viewModel: BrowserViewModel,
    hubViewModel: HubViewModel,
    webViewActions: WebViewActionsHandle,
    navController: NavHostController,
): HomeCallbacks {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(viewModel, hubViewModel, webViewActions, navController, keyboardController, focusManager) {
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
            onSeeAllHistory = { navController.navigate(AppDestination.History.route) },
            onNewIncognitoTab = hubViewModel::onNewIncognitoTab,
        )
    }
}

/** Spec 011 / 013 / 015 — one-shot snackbars from [BrowserUiState]; extracted from the host. */
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

/** Spec 019 — snackbars for hub actions that finish after the sheet has closed. */
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

/**
 * Spec 007 / 011 / 015 — the always-mounted WebView, keyed by the active tab so Compose
 * disposes the previous instance on a switch (R8 of Spec 011). The error overlay and the bars
 * live in [BrowserScreen], not here.
 */
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
    modifier: Modifier = Modifier,
) {
    // Spec 015 FR-010 – FR-012 — downloads pass through the storage-permission gate before
    // reaching the ViewModel. On API 29+ the gate is a straight pass-through.
    val onDownloadRequested = rememberDownloadRequestHandler(
        onGranted = viewModel::onDownloadRequested,
        onDenied = viewModel::onStoragePermissionDenied,
    )
    Box(modifier = modifier) {
        androidx.compose.runtime.key(activeTabId) {
            // Spec 021 research R5 — a tab a page opened adopts the page's WebView.
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
                modifier = Modifier.fillMaxSize(),
                adopted = adopted,
            )
        }
    }
}

/**
 * Spec 009 — wires the address-bar callbacks. The `onSubmit` lambda chain
 * implements FR-013a: dismiss keyboard → clear focus → load URL, in that
 * order, and only on non-empty submits.
 */
@Composable
private fun rememberAddressBarCallbacks(
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
): AddressBarCallbacks {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(viewModel, webViewActions, keyboardController, focusManager) {
        AddressBarCallbacks(
            onTextChange = viewModel::onAddressBarTextChange,
            onFocusChange = viewModel::onAddressBarFocusChange,
            onSubmit = {
                viewModel.onAddressBarSubmit { url ->
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    webViewActions.loadUrl(url)
                }
            },
            onClear = viewModel::onAddressBarClear,
        )
    }
}
