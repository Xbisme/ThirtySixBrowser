@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.tabs.components.CloseAllIncognitoConfirmDialog
import com.raumanian.thirtysix.browser.presentation.tabs.components.CloseAllTabsConfirmDialog
import com.raumanian.thirtysix.browser.presentation.tabs.components.TabSwitcherCard
import com.raumanian.thirtysix.browser.presentation.tabs.components.TabSwitcherNewIncognitoTabCard
import com.raumanian.thirtysix.browser.presentation.tabs.components.TabSwitcherNewTabCard
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 011 — tab switcher screen (`AppDestination.Tabs` route — placeholder
 * route reserved by Spec 002, now hosting the real switcher).
 *
 * Layout: [Scaffold] with a plural-aware count title ("5 tabs", "1 tab", etc.
 * per locale's CLDR plural rules — FR-015 / M4) + a "Close all tabs" action
 * button in the [TopAppBar] (FR-012) + [LazyVerticalGrid] body with adaptive
 * columns. Each `state.tabs` entry renders a [TabSwitcherCard]; the new-tab
 * affordance ([TabSwitcherNewTabCard]) is the last item in the grid (FR-011),
 * disabled at `MAX_TABS` (FR-016).
 *
 * NavController coupling (M3): the Composable collects [TabsViewModel.popBackEvent]
 * via a `LaunchedEffect(Unit)` and calls `navController.popBackStack(...)` on
 * each emission. NO `LaunchedEffect(activeTabId)` — that heuristic was
 * rejected for false-positives on initial composition (analyze M3 remediation).
 *
 * Spec 011 (T046 / T047 / US3):
 *  - Close × on each card wired to `viewModel.onCloseTab(tabId)`.
 *  - Close-all action triggers [CloseAllTabsConfirmDialog]; confirm dispatches
 *    [TabsViewModel.onCloseAllConfirmed], dismiss dispatches `onCloseAllDismissed`.
 *  - [TabsErrorEvent.MaxTabsReached] surfaces as a snackbar via
 *    [SnackbarHostState]; consumed via `viewModel.consumeErrorEvent()` after
 *    display (FR-016).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: TabsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val maxTabsMessage = stringResource(R.string.browser_max_tabs_reached)
    val maxIncognitoMessage = stringResource(
        R.string.tabs_error_max_incognito_tabs_reached,
        BrowserLimits.MAX_INCOGNITO_TABS,
    )

    LaunchedEffect(Unit) {
        viewModel.popBackEvent.collect {
            navController.popBackStack(AppDestination.Browser.route, inclusive = false)
        }
    }

    TabsErrorEventEffect(
        errorEvent = state.errorEvent,
        maxTabsMessage = maxTabsMessage,
        maxIncognitoMessage = maxIncognitoMessage,
        snackbarHostState = snackbarHostState,
        consumeErrorEvent = viewModel::consumeErrorEvent,
    )

    TabsConfirmDialogs(state = state, viewModel = viewModel)

    // Spec 011 favicon amendment (2026-05-03) — re-read favicon files when
    // a fresh icon arrives. `faviconVersion` bumps on every successful save;
    // collectAsStateWithLifecycle pulls the latest counter so the grid
    // re-composes (and TabSwitcherCard's `remember(faviconFile.length())`
    // re-decodes the bitmap).
    val faviconVersion by viewModel.faviconVersion.collectAsStateWithLifecycle()

    // Spec 011 Q4 amendment (2026-05-03) — same pattern for screenshots.
    // Bumps on save / delete / clearAll so the grid sees freshly-captured
    // previews and falls back to the placeholder after a tab close.
    val screenshotVersion by viewModel.screenshotVersion.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TEST_TAG_TABS_SCREEN),
        topBar = {
            TabsTopBar(
                tabCount = state.tabs.size,
                incognitoTabCount = state.incognitoTabCount,
                onCloseAllRequested = viewModel::onCloseAllRequested,
                onCloseAllIncognitoRequested = viewModel::onCloseAllIncognitoRequested,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        TabsGrid(
            state = state,
            padding = padding,
            faviconVersion = faviconVersion,
            screenshotVersion = screenshotVersion,
            faviconFor = viewModel::faviconFor,
            screenshotFor = viewModel::screenshotFor,
            onTabClick = viewModel::onTabClick,
            onTabClose = viewModel::onCloseTab,
            onNewTabClick = viewModel::onNewTabClick,
            onNewIncognitoTabClick = viewModel::onNewIncognitoTabClick,
        )
    }
}

/**
 * Spec 012 — extracted to keep [TabsScreen] under detekt's `LongMethod = 60`.
 */
@Composable
private fun TabsErrorEventEffect(
    errorEvent: TabsErrorEvent?,
    maxTabsMessage: String,
    maxIncognitoMessage: String,
    snackbarHostState: SnackbarHostState,
    consumeErrorEvent: () -> Unit,
) {
    LaunchedEffect(errorEvent) {
        when (errorEvent) {
            is TabsErrorEvent.MaxTabsReached -> {
                snackbarHostState.showSnackbar(maxTabsMessage)
                consumeErrorEvent()
            }
            is TabsErrorEvent.MaxIncognitoTabsReached -> {
                snackbarHostState.showSnackbar(maxIncognitoMessage)
                consumeErrorEvent()
            }
            null -> Unit
        }
    }
}

/**
 * Spec 012 — extracted to keep [TabsScreen] under detekt's `LongMethod = 60`.
 */
@Composable
private fun TabsConfirmDialogs(state: TabsUiState, viewModel: TabsViewModel) {
    if (state.isCloseAllDialogVisible) {
        CloseAllTabsConfirmDialog(
            onConfirm = viewModel::onCloseAllConfirmed,
            onDismiss = viewModel::onCloseAllDismissed,
        )
    }
    if (state.isCloseAllIncognitoDialogVisible) {
        CloseAllIncognitoConfirmDialog(
            count = state.incognitoTabCount,
            onConfirm = viewModel::onCloseAllIncognitoConfirmed,
            onDismiss = viewModel::onCloseAllIncognitoDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsTopBar(
    tabCount: Int,
    incognitoTabCount: Int,
    onCloseAllRequested: () -> Unit,
    onCloseAllIncognitoRequested: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(
                    id = R.plurals.tabs_switcher_title_count,
                    count = tabCount,
                    tabCount,
                ),
            )
        },
        actions = {
            // Spec 012 — "Close all incognito" affordance, visible iff
            // incognitoTabCount > 0 (US5 acceptance scenarios 1+2).
            if (incognitoTabCount > 0) {
                IconButton(
                    onClick = onCloseAllIncognitoRequested,
                    modifier = Modifier.testTag(TEST_TAG_CLOSE_ALL_INCOGNITO_ACTION),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(
                            R.string.tabs_action_close_all_incognito,
                        ),
                    )
                }
            }
            IconButton(
                onClick = onCloseAllRequested,
                modifier = Modifier.testTag(TEST_TAG_CLOSE_ALL_ACTION),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.tabs_switcher_close_all),
                )
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun TabsGrid(
    state: TabsUiState,
    padding: PaddingValues,
    faviconVersion: Long,
    screenshotVersion: Long,
    faviconFor: (String) -> java.io.File?,
    screenshotFor: (Long) -> java.io.File?,
    onTabClick: (Long) -> Unit,
    onTabClose: (Long) -> Unit,
    onNewTabClick: () -> Unit,
    onNewIncognitoTabClick: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TAB_CARD_MIN_WIDTH_DP),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        items(items = state.tabs, key = { tab -> tab.id }) { tab ->
            // Re-derive favicon + screenshot paths per *Version bump so
            // the `remember(file?.length())` cache keys inside
            // TabSwitcherCard see new file mtime/size after a save.
            @Suppress("UNUSED_EXPRESSION")
            faviconVersion
            @Suppress("UNUSED_EXPRESSION")
            screenshotVersion
            TabSwitcherCard(
                tab = tab,
                isActive = tab.id == state.activeTabId,
                // Spec 012 — incognito cards never read from caches; pass
                // null so TabSwitcherCard's branch (T051) renders the
                // placeholder regardless of any (theoretical) cache file.
                faviconFile = if (tab.isIncognito) null else faviconFor(tab.url),
                screenshotFile = if (tab.isIncognito) null else screenshotFor(tab.id),
                onClick = { onTabClick(tab.id) },
                onCloseClick = { onTabClose(tab.id) },
            )
        }
        // Normal new-tab card — disabled at MAX_TABS (existing Spec 011 logic).
        // Counts only normal tabs against MAX_TABS per Q1 independent caps.
        val normalTabCount = state.tabs.count { !it.isIncognito }
        item(key = NEW_TAB_CARD_KEY) {
            TabSwitcherNewTabCard(
                enabled = normalTabCount < BrowserLimits.MAX_TABS,
                onClick = onNewTabClick,
            )
        }
        // Spec 012 — incognito new-tab card. Always rendered; disabled at
        // MAX_INCOGNITO_TABS (FR-004).
        item(key = NEW_INCOGNITO_TAB_CARD_KEY) {
            TabSwitcherNewIncognitoTabCard(
                enabled = state.incognitoTabCount < BrowserLimits.MAX_INCOGNITO_TABS,
                onClick = onNewIncognitoTabClick,
            )
        }
    }
}

const val TEST_TAG_TABS_SCREEN: String = "tabs_screen"
const val TEST_TAG_CLOSE_ALL_ACTION: String = "tabs_close_all_action"
const val TEST_TAG_CLOSE_ALL_INCOGNITO_ACTION: String = "tabs_close_all_incognito_action"

private val TAB_CARD_MIN_WIDTH_DP = 160.dp
private const val NEW_TAB_CARD_KEY: String = "tabs_new_tab_card"
private const val NEW_INCOGNITO_TAB_CARD_KEY: String = "tabs_new_incognito_tab_card"
