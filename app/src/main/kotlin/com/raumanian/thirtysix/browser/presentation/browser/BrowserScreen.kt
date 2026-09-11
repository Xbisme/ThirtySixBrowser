@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBar
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBarCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserErrorState
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserLoadingIndicator
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserOverflowMenu
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserOverflowMenuCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.IncognitoIndicator
import com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBar
import com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBarCallbacks
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.tabs.TabsErrorEvent
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 007 + Spec 008 + Spec 009 + Spec 011 — top-level Browser screen.
 *
 * Layout (Spec 008): Material 3 [Scaffold] with always-visible
 * [NavigationBottomBar] in the `bottomBar` slot (FR-018) and the WebView
 * surface in the content slot.
 *
 * Spec 009 — adds the [AddressBar] in the [Scaffold] `topBar` slot. Always
 * visible (FR-001/02/03), even during error overlay.
 *
 * Spec 011:
 * - Receives [navController] so the 5th BottomAppBar button (single-tap)
 *   navigates to the tab switcher route.
 * - Long-press on that same button dispatches [BrowserViewModel.onLongPressNewTab]
 *   for the inline new-tab path (Q2 / R6).
 * - Reads `tabCount` + `activeTab` StateFlows from the ViewModel — `tabCount`
 *   drives the bottom-bar `BadgedBox`, and `activeTab.id` drives a
 *   `LaunchedEffect` that issues `webViewActions.loadUrl(activeTab.url)` on
 *   every tab switch (R8 — single live WebView, recreate on switch).
 * - The WebView is wrapped in `key(activeTab.id)` so Compose disposes the
 *   prior instance and instantiates a fresh one on activation, re-applying
 *   Spec 007's lockdown settings (FR-028).
 * - Surfaces a snackbar for [BrowserUiState.tabsEvent] (FR-016 cap-reached)
 *   then calls [BrowserViewModel.consumeTabsEvent].
 */
@Composable
@Suppress("LongMethod") // Top-level Compose host wires Scaffold, snackbar, three LaunchedEffects.
fun BrowserScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }
    val snackbarHostState = remember { SnackbarHostState() }
    val maxTabsMessage = stringResource(R.string.browser_max_tabs_reached)
    val bookmarkAddedMessage = stringResource(R.string.bookmark_added_snackbar)
    // Spec 015 — resolved here rather than inside the effect because `stringResource` is a
    // Composable call and cannot be made from a coroutine body.
    val downloadStartedTemplate = stringResource(R.string.downloads_started_snackbar)
    val downloadServiceUnavailableMessage = stringResource(R.string.downloads_service_unavailable)
    val storagePermissionDeniedMessage = stringResource(R.string.downloads_storage_permission_denied)
    val storagePermissionSettingsMessage =
        stringResource(R.string.downloads_storage_permission_denied_permanently)
    val bookmarkRemovedMessage = stringResource(R.string.bookmark_removed_snackbar)

    BrowserBackHandlers(state = state, webViewActions = webViewActions)

    LaunchedEffect(activeTab?.id) {
        // Spec 011 — tab-switch driver. On every activeTab.id change, issue
        // a fresh URL load on the (key-recomposed) WebView.
        activeTab?.let { webViewActions.loadUrl(it.url) }
    }

    LaunchedEffect(state.tabsEvent) {
        // Spec 011 — surface MaxTabsReached snackbar from BrowserScreen long-press.
        if (state.tabsEvent is TabsErrorEvent.MaxTabsReached) {
            snackbarHostState.showSnackbar(maxTabsMessage)
            viewModel.consumeTabsEvent()
        }
    }

    LaunchedEffect(state.bookmarkSnackbarEvent) {
        // Spec 013 — surface star toggle confirmation snackbar (FR-001 / FR-003).
        when (state.bookmarkSnackbarEvent) {
            BookmarkSnackbarEvent.Added -> {
                snackbarHostState.showSnackbar(bookmarkAddedMessage)
                viewModel.consumeBookmarkSnackbarEvent()
            }
            BookmarkSnackbarEvent.Removed -> {
                snackbarHostState.showSnackbar(bookmarkRemovedMessage)
                viewModel.consumeBookmarkSnackbarEvent()
            }
            null -> Unit
        }
    }

    LaunchedEffect(state.downloadSnackbarEvent) {
        // Spec 015 FR-002 / FR-008a / FR-012 — confirm or explain, over the page the user
        // is still on, without blocking it.
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

    val overflowExpanded = remember { mutableStateOf(false) }

    val addressBarCallbacks = rememberAddressBarCallbacks(viewModel, webViewActions)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BrowserTopBar(
                state = state,
                callbacks = addressBarCallbacks,
                onStarTapped = viewModel::onStarTapped,
            )
        },
        bottomBar = {
            NavigationBottomBar(
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isLoading = state.loadingState is LoadingState.Loading,
                tabCount = tabCount,
                callbacks = rememberBottomBarCallbacks(
                    state = state,
                    webViewActions = webViewActions,
                    onStopRequested = viewModel::onLoadStopped,
                    onOverflowClick = { overflowExpanded.value = true },
                    onTabsSwitcherClick = {
                        navController.navigate(AppDestination.Tabs.route)
                    },
                    onTabsSwitcherLongClick = viewModel::onLongPressNewTab,
                ),
            )
            // Spec 015 FR-042 — anchored to the bar so it opens over the overflow control.
            BrowserOverflowMenu(
                expanded = overflowExpanded.value,
                onDismiss = { overflowExpanded.value = false },
                callbacks = BrowserOverflowMenuCallbacks(
                    onBookmarksClick = {
                        overflowExpanded.value = false
                        navController.navigate(AppDestination.Bookmarks.route)
                    },
                    onHistoryClick = {
                        overflowExpanded.value = false
                        navController.navigate(AppDestination.History.route)
                    },
                    onDownloadsClick = {
                        overflowExpanded.value = false
                        navController.navigate(AppDestination.Downloads.route)
                    },
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        BrowserScaffoldContent(
            state = state,
            viewModel = viewModel,
            webViewActions = webViewActions,
            activeTabId = activeTab?.id ?: 0L,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
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
 * Spec 009 — extracted from [BrowserScreen]'s `content` slot to keep the host
 * function under detekt's `LongMethod` threshold (60 lines). Renders the
 * always-mounted [BrowserWebView] plus the conditional loading-indicator and
 * error-state overlays. Spec 008 ordering preserved: WebView at the back,
 * loading on top, error fully covers (FR-001/02/03 — bar always visible
 * because it lives in the Scaffold's `topBar` slot, not here).
 *
 * Spec 011 — wraps `BrowserWebView` in `key(activeTabId)` so Compose
 * disposes the prior WebView (Spec 007 `DisposableEffect` cleanup fires) and
 * instantiates a fresh one on every tab switch (R8 — single live WebView).
 */
/**
 * Spec 012 — Scaffold topBar slot. When the active tab is incognito (FR-015),
 * the [IncognitoIndicator] precedes the [AddressBar] inside a Row; on normal
 * tabs the AddressBar renders alone (preserves Spec 009 visual treatment).
 *
 * Extracted to keep [BrowserScreen] under detekt's `LongMethod = 60`.
 */
@Composable
private fun BrowserTopBar(
    state: BrowserUiState,
    callbacks: com.raumanian.thirtysix.browser.presentation.browser.components.AddressBarCallbacks,
    onStarTapped: () -> Unit,
) {
    val canBookmark = canBookmarkCurrentPage(state)
    if (state.isIncognito || canBookmark) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
        ) {
            if (state.isIncognito) {
                IncognitoIndicator()
            }
            Box(modifier = Modifier.weight(1f)) {
                AddressBar(state = state, callbacks = callbacks)
            }
            if (canBookmark) {
                StarBookmarkButton(
                    isBookmarked = state.isBookmarked,
                    onClick = onStarTapped,
                )
            }
        }
    } else {
        AddressBar(state = state, callbacks = callbacks)
    }
}

/**
 * Spec 013 FR-004 — star icon is hidden (rather than disabled-grey) when the
 * current page cannot be bookmarked: incognito tab, error state, or non-http(s)
 * scheme. Per research.md R11.
 */
private fun canBookmarkCurrentPage(state: BrowserUiState): Boolean {
    val url = state.currentUrl
    val isHttp = url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
    return !state.isIncognito &&
        state.loadingState !is LoadingState.Failed &&
        url.isNotBlank() &&
        isHttp
}

/**
 * Spec 013 FR-001..003 — star icon rendered trailing the address bar. Tap
 * adds a bookmark for the current page; second tap removes the most recently
 * created bookmark for that URL (Q4 toggle semantic).
 */
@Composable
private fun StarBookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
) {
    val cd = if (isBookmarked) {
        stringResource(R.string.bookmark_action_star_remove_cd)
    } else {
        stringResource(R.string.bookmark_action_star_add_cd)
    }
    IconButton(onClick = onClick, modifier = Modifier.testTag(TEST_TAG_BROWSER_STAR_ICON)) {
        Icon(
            // Spec 013 — `material-icons-core` lacks `Bookmark` / `BookmarkBorder`;
            // use the Favorite / FavoriteBorder pair instead. Same filled-vs-outline
            // affordance, no new package dependency (research.md R11 + R-icon-fallback).
            imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = cd,
        )
    }
}

internal const val TEST_TAG_BROWSER_STAR_ICON: String = "browser_star_icon"

@Composable
private fun BrowserScaffoldContent(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
    activeTabId: Long,
    modifier: Modifier = Modifier,
) {
    // Spec 015 FR-010 – FR-012 — downloads pass through the storage-permission gate before
    // reaching the ViewModel. On API 29+ the gate is a straight pass-through and no
    // permission is ever requested.
    val onDownloadRequested = rememberDownloadRequestHandler(
        onGranted = viewModel::onDownloadRequested,
        onDenied = viewModel::onStoragePermissionDenied,
    )

    Box(modifier = modifier) {
        androidx.compose.runtime.key(activeTabId) {
            BrowserWebView(
                state = state,
                homeUrl = viewModel.homeUrl,
                actions = webViewActions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = viewModel::onLoadStarted,
                    onProgressChanged = viewModel::onProgressChanged,
                    onLoadFinished = viewModel::onLoadFinished,
                    onLoadFailed = viewModel::onLoadFailed,
                    onDownloadRequested = onDownloadRequested,
                ),
                navigationCallbacks = BrowserNavigationCallbacks(
                    onUrlChange = viewModel::onUrlChanged,
                    onCanGoBackChange = viewModel::onCanGoBackChanged,
                    onCanGoForwardChange = viewModel::onCanGoForwardChanged,
                    onTitleChange = viewModel::onTitleReceived,
                    onIconReceived = viewModel::onIconReceived,
                    onScreenshotReady = viewModel::onScreenshotReady,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
        (state.loadingState as? LoadingState.Loading)?.let { loading ->
            BrowserLoadingIndicator(
                progress = loading.progress,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        (state.loadingState as? LoadingState.Failed)?.let { failed ->
            BrowserErrorState(
                reason = failed.reason,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Spec 008 — extracted to keep [BrowserScreen] under detekt's `LongMethod`
 * threshold. Builds the click lambdas the bottom bar dispatches:
 * Back/Forward route directly through [WebViewActionsHandle]; Reload/Stop
 * dispatches conditionally on [BrowserUiState.loadingState] (Stop semantic
 * during Loading also flips state via [onStopRequested] so the loading
 * indicator hides immediately per FR-006); Home calls
 * [WebViewActionsHandle.loadHome] which the WebView factory wired to
 * `loadUrl(homeUrl)`.
 *
 * Spec 011 — adds the 5th-button single-tap (switcher navigate) and
 * long-press (inline new tab) callbacks. Both lambdas hoisted from
 * `BrowserScreen` so the bar stays purely presentational.
 */
@Composable
@Suppress("LongParameterList")
private fun rememberBottomBarCallbacks(
    state: BrowserUiState,
    webViewActions: WebViewActionsHandle,
    onStopRequested: () -> Unit,
    onOverflowClick: () -> Unit,
    onTabsSwitcherClick: () -> Unit,
    onTabsSwitcherLongClick: () -> Unit,
): NavigationBottomBarCallbacks = NavigationBottomBarCallbacks(
    onBack = { webViewActions.goBack() },
    onForward = { webViewActions.goForward() },
    onReloadOrStop = {
        if (state.loadingState is LoadingState.Loading) {
            webViewActions.stopLoading()
            onStopRequested()
        } else {
            webViewActions.reload()
        }
    },
    onHome = { webViewActions.loadHome() },
    onOverflowClick = onOverflowClick,
    onTabsSwitcherClick = onTabsSwitcherClick,
    onTabsSwitcherLongClick = onTabsSwitcherLongClick,
)

/**
 * Spec 009 — wires the address-bar callbacks. The `onSubmit` lambda chain
 * implements FR-013a: dismiss keyboard → clear focus → load URL, in that
 * order. The dismiss + clear happen inside the URL-consumer lambda passed
 * to [BrowserViewModel.onAddressBarSubmit] so they only execute on
 * non-empty submits (the ViewModel skips invocation on `Empty`).
 */
@Composable
private fun rememberAddressBarCallbacks(
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
): AddressBarCallbacks {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return AddressBarCallbacks(
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
