@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ThirtySixChip
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.domain.model.FireState
import com.raumanian.thirtysix.browser.presentation.browser.components.FireConfirmDialog
import com.raumanian.thirtysix.browser.presentation.browser.fire.FireViewModel
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.tabs.components.CloseAllIncognitoConfirmDialog
import com.raumanian.thirtysix.browser.presentation.tabs.components.CloseAllTabsConfirmDialog
import com.raumanian.thirtysix.browser.presentation.tabs.components.TabCreateCard
import com.raumanian.thirtysix.browser.presentation.tabs.components.TabSwitcherCard
import java.io.File

/**
 * Spec 011 tab switcher, restyled by Spec 019 FR-040 (design system Tabs artboard): a large
 * plural-aware title ("5 tabs"), Open / Incognito chips that switch the mode (clarification
 * Q3 — the Incognito chip appears only while incognito tabs exist), one close-all action for
 * the mode on screen, and an adaptive grid of image cards — two columns at 360dp, more when
 * wider — ending with the mode's create card.
 *
 * NavController coupling (Spec 011 M3): [TabsViewModel.popBackEvent] pops back to the browser.
 */
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

    LaunchedEffect(viewModel) {
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

    // Spec 011 — favicon / screenshot saves bump these counters so cards re-read their files.
    val faviconVersion by viewModel.faviconVersion.collectAsStateWithLifecycle()
    val screenshotVersion by viewModel.screenshotVersion.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TEST_TAG_TABS_SCREEN),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TabsHeader(
                state = state,
                navController = navController,
                onModeSelected = viewModel::onModeSelected,
                onCloseAll = if (state.mode == TabsMode.Incognito) {
                    viewModel::onCloseAllIncognitoRequested
                } else {
                    viewModel::onCloseAllRequested
                },
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
            onCreate = if (state.mode == TabsMode.Incognito) {
                viewModel::onNewIncognitoTabClick
            } else {
                viewModel::onNewTabClick
            },
        )
    }
}

/** Spec 024 FR-001 — the Tabs header's Fire button; disabled while a run is in progress (INV-5). */
private data class TabsFireButton(val enabled: Boolean, val onClick: () -> Unit)

/** Spec 024 FR-001, FR-002 — the Tabs header's Fire button and the confirmation it opens. */
@Composable
private fun rememberTabsFire(
    navController: NavHostController,
    fire: FireViewModel = hiltViewModel(),
): TabsFireButton {
    val fireState by fire.state.collectAsStateWithLifecycle()
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    if (confirmOpen) {
        FireConfirmDialog(
            onConfirm = {
                confirmOpen = false
                // Research R10 — back to the browser first; its overlay shows the run.
                navController.popBackStack(AppDestination.Browser.route, inclusive = false)
                fire.fire()
            },
            onDismiss = { confirmOpen = false },
        )
    }
    return TabsFireButton(enabled = fireState == FireState.Idle, onClick = { confirmOpen = true })
}

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

/** Back, the close-all action for the current mode, the large title and the mode chips. */
@Composable
private fun TabsHeader(
    state: TabsUiState,
    navController: NavHostController,
    onModeSelected: (TabsMode) -> Unit,
    onCloseAll: () -> Unit,
) {
    val fire = rememberTabsFire(navController = navController)
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
                onClick = { navController.popBackStack() },
                style = RoundIconStyle.Bare,
            )
            Spacer(modifier = Modifier.weight(1f))
            // Spec 024 FR-001 — Fire, beside close-all; disabled while a run is in progress (INV-5).
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
        ModeChips(incognito = incognito, showIncognito = state.incognitoTabCount > 0, onModeSelected = onModeSelected)
    }
}

/** Open / Incognito chips; the Incognito chip appears only while incognito tabs exist (Q3). */
@Composable
private fun ModeChips(incognito: Boolean, showIncognito: Boolean, onModeSelected: (TabsMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(horizontal = Spacing.sm),
    ) {
        ThirtySixChip(
            label = stringResource(R.string.tabs_chip_open),
            selected = !incognito,
            onClick = { onModeSelected(TabsMode.Open) },
            leadingIcon = AppIcons.Tab,
            modifier = Modifier.testTag(TEST_TAG_TABS_CHIP_OPEN),
        )
        if (showIncognito) {
            ThirtySixChip(
                label = stringResource(R.string.tabs_chip_incognito),
                selected = incognito,
                onClick = { onModeSelected(TabsMode.Incognito) },
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
    faviconVersion: Long,
    screenshotVersion: Long,
    faviconFor: (String) -> File?,
    screenshotFor: (Long) -> File?,
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
            // Incognito cards never read the caches (Spec 012); the version pair re-reads files
            // after a save without recomposing anything else.
            val files = remember(tab.id, tab.url, tab.isIncognito, faviconVersion, screenshotVersion) {
                if (tab.isIncognito) null to null else faviconFor(tab.url) to screenshotFor(tab.id)
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

/** Spec 024 FR-001 — the Fire button in the Tabs header. */
const val TEST_TAG_TABS_FIRE: String = "tabs_fire_action"
const val TEST_TAG_CLOSE_ALL_ACTION: String = "tabs_close_all_action"
const val TEST_TAG_CLOSE_ALL_INCOGNITO_ACTION: String = "tabs_close_all_incognito_action"
const val TEST_TAG_TABS_CHIP_OPEN: String = "tabs_chip_open"
const val TEST_TAG_TABS_CHIP_INCOGNITO: String = "tabs_chip_incognito"

private const val CONTENT_TYPE_TAB = "tab"
private const val CONTENT_TYPE_CREATE = "create"
