@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ThirtySixChip
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component.CloseAllIncognitoConfirmDialog
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component.CloseAllTabsConfirmDialog
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component.TabCreateCard
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component.TabSwitcherCard
import kotlinx.coroutines.launch

/**
 * Tab switcher: a plural-aware title, Open / Incognito mode chips, one close-all action for the
 * mode on screen, and an adaptive grid of image cards ending with the mode's create card.
 */
@Composable
fun TabsRoute(
    navController: NavHostController,
    fireButton: FireButton,
    modifier: Modifier = Modifier,
    onShowBrowser: () -> Unit = {},
    viewModel: TabsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val maxTabsMessage = stringResource(R.string.browser_max_tabs_reached)
    val maxIncognitoMessage = stringResource(
        R.string.tabs_error_max_incognito_tabs_reached,
        BrowserLimits.MAX_INCOGNITO_TABS,
    )
    EventEffect(viewModel.events) { event ->
        when (event) {
            TabsEvent.ShowBrowser -> onShowBrowser()
            // Shown apart from the collector so a showing message never holds up navigation.
            is TabsEvent.Error -> scope.launch {
                snackbarHostState.showSnackbar(
                    when (event.error) {
                        TabsErrorEvent.MaxTabsReached -> maxTabsMessage
                        TabsErrorEvent.MaxIncognitoTabsReached -> maxIncognitoMessage
                    },
                )
            }
        }
    }

    // Favicon / screenshot saves bump these counters so cards re-read their files.
    val faviconVersion by viewModel.faviconVersion.collectAsStateWithLifecycle()
    val screenshotVersion by viewModel.screenshotVersion.collectAsStateWithLifecycle()

    TabsScreen(
        state = state,
        images = viewModel.images,
        imageVersions = faviconVersion to screenshotVersion,
        fireButton = fireButton,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onBack = { navController.popBackStack() },
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
fun TabsScreen(
    state: TabsUiState,
    images: TabImages,
    imageVersions: Pair<Long, Long>,
    fireButton: FireButton,
    snackbarHostState: SnackbarHostState,
    onAction: (TabsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TabsConfirmDialogs(state = state, onAction = onAction)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TEST_TAG_TABS_SCREEN),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TabsHeader(
                state = state,
                onBack = onBack,
                fire = fireButton,
                onModeSelect = { onAction(TabsAction.ModeSelected(it)) },
                onCloseAll = if (state.mode == TabsMode.Incognito) {
                    { onAction(TabsAction.CloseAllIncognitoRequested) }
                } else {
                    { onAction(TabsAction.CloseAllRequested) }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        TabsGrid(
            state = state,
            padding = padding,
            imageVersions = imageVersions,
            images = images,
            onTabClick = { onAction(TabsAction.TabClick(it)) },
            onTabClose = { onAction(TabsAction.CloseTab(it)) },
            onCreate = if (state.mode == TabsMode.Incognito) {
                { onAction(TabsAction.NewIncognitoTabClick) }
            } else {
                { onAction(TabsAction.NewTabClick) }
            },
        )
    }
}

@Composable
private fun TabsConfirmDialogs(state: TabsUiState, onAction: (TabsAction) -> Unit) {
    if (state.isCloseAllDialogVisible) {
        CloseAllTabsConfirmDialog(
            onConfirm = { onAction(TabsAction.CloseAllConfirmed) },
            onDismiss = { onAction(TabsAction.CloseAllDismissed) },
        )
    }
    if (state.isCloseAllIncognitoDialogVisible) {
        CloseAllIncognitoConfirmDialog(
            count = state.incognitoTabCount,
            onConfirm = { onAction(TabsAction.CloseAllIncognitoConfirmed) },
            onDismiss = { onAction(TabsAction.CloseAllIncognitoDismissed) },
        )
    }
}

/** Back, the close-all action for the current mode, the large title and the mode chips. */
@Composable
private fun TabsHeader(
    state: TabsUiState,
    onBack: () -> Unit,
    fire: FireButton,
    onModeSelect: (TabsMode) -> Unit,
    onCloseAll: () -> Unit,
) {
    val incognito = state.mode == TabsMode.Incognito
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RoundIconButton(
                icon = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.browser_action_back),
                onClick = onBack,
                style = RoundIconStyle.Bare,
            )
            Spacer(modifier = Modifier.weight(1f))
            // Disabled while a Fire run is in progress.
            RoundIconButton(
                icon = AppIcons.Fire,
                contentDescription = stringResource(R.string.fire_action_tabs_cd),
                onClick = fire.onClick,
                style = RoundIconStyle.Bare,
                enabled = fire.enabled,
                modifier = Modifier.testTag(TEST_TAG_TABS_FIRE),
            )
            RoundIconButton(
                icon = AppIcons.DeleteSweep,
                contentDescription = stringResource(
                    if (incognito) R.string.tabs_action_close_all_incognito else R.string.tabs_switcher_close_all,
                ),
                onClick = onCloseAll,
                style = RoundIconStyle.Bare,
                enabled = state.visibleTabs.isNotEmpty(),
                modifier = Modifier.testTag(
                    if (incognito) TEST_TAG_CLOSE_ALL_INCOGNITO_ACTION else TEST_TAG_CLOSE_ALL_ACTION,
                ),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.tabs_switcher_title_count, state.tabs.size, state.tabs.size),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = Spacing.sm)
                .semantics { heading() },
        )
        ModeChips(incognito = incognito, showIncognito = state.incognitoTabCount > 0, onModeSelect = onModeSelect)
    }
}

/** Open / Incognito chips; the Incognito chip appears only while incognito tabs exist. */
@Composable
private fun ModeChips(incognito: Boolean, showIncognito: Boolean, onModeSelect: (TabsMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(horizontal = Spacing.sm),
    ) {
        ThirtySixChip(
            label = stringResource(R.string.tabs_chip_open),
            selected = !incognito,
            onClick = { onModeSelect(TabsMode.Open) },
            leadingIcon = AppIcons.Tab,
            modifier = Modifier.testTag(TEST_TAG_TABS_CHIP_OPEN),
        )
        if (showIncognito) {
            ThirtySixChip(
                label = stringResource(R.string.tabs_chip_incognito),
                selected = incognito,
                onClick = { onModeSelect(TabsMode.Incognito) },
                leadingIcon = AppIcons.Incognito,
                modifier = Modifier.testTag(TEST_TAG_TABS_CHIP_INCOGNITO),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun TabsGrid(
    state: TabsUiState,
    padding: PaddingValues,
    imageVersions: Pair<Long, Long>,
    images: TabImages,
    onTabClick: (Long) -> Unit,
    onTabClose: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    val createEnabled = if (state.mode == TabsMode.Incognito) {
        state.incognitoTabCount < BrowserLimits.MAX_INCOGNITO_TABS
    } else {
        state.tabs.size - state.incognitoTabCount < BrowserLimits.MAX_TABS
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Dimens.tabCardMinWidth),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = padding.calculateTopPadding() + Spacing.xs,
            bottom = padding.calculateBottomPadding() + Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = state.visibleTabs, key = { tab -> tab.id }, contentType = { CONTENT_TYPE_TAB }) { tab ->
            // Incognito cards never read the caches; the version pair re-reads files
            // after a save without recomposing anything else.
            val files = remember(tab.id, tab.url, tab.isIncognito, imageVersions) {
                if (tab.isIncognito) null to null else images.favicon(tab.url) to images.screenshot(tab.id)
            }
            TabSwitcherCard(
                tab = tab,
                isActive = tab.id == state.activeTabId,
                faviconFile = files.first,
                screenshotFile = files.second,
                onClick = { onTabClick(tab.id) },
                onCloseClick = { onTabClose(tab.id) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = state.mode, contentType = CONTENT_TYPE_CREATE) {
            TabCreateCard(mode = state.mode, enabled = createEnabled, onClick = onCreate)
        }
    }
}

const val TEST_TAG_TABS_SCREEN: String = "tabs_screen"

const val TEST_TAG_TABS_FIRE: String = "tabs_fire_action"
const val TEST_TAG_CLOSE_ALL_ACTION: String = "tabs_close_all_action"
const val TEST_TAG_CLOSE_ALL_INCOGNITO_ACTION: String = "tabs_close_all_incognito_action"
const val TEST_TAG_TABS_CHIP_OPEN: String = "tabs_chip_open"
const val TEST_TAG_TABS_CHIP_INCOGNITO: String = "tabs_chip_incognito"

private const val CONTENT_TYPE_TAB = "tab"
private const val CONTENT_TYPE_CREATE = "create"
