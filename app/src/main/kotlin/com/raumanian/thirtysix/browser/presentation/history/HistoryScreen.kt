@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.presentation.history.components.ClearAllHistoryConfirmDialog
import com.raumanian.thirtysix.browser.presentation.history.components.EmptyHistoryState
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryActionSheet
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryDayHeader
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryRow
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryTopBar
import com.raumanian.thirtysix.browser.presentation.history.components.NoHistoryMatchesState
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination

/**
 * Spec 014 — History screen: top bar (title · back · search · clear-all), the grouped
 * list with its two zero-result branches, the long-press action sheet, the clear-all
 * confirmation dialog, and a snackbar host for one-shot action feedback.
 *
 * `FaviconCache` is exposed via the ViewModel (rather than via Hilt entry points
 * or a separate Composable parameter) so test seams stay simple — instrumented
 * tests can swap the VM with a fake whose `faviconCache` field returns a noop.
 */
@Composable
fun HistoryScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Spec 014 FR-010 / FR-019 — when the ViewModel signals a tap-replace (or a
    // successful open-in-new-tab), pop back to BrowserScreen so the user immediately
    // sees the URL load in the now-active tab.
    LaunchedEffect(state.openUrl) {
        if (state.openUrl != null) {
            navController.popBackStack(AppDestination.Browser.route, inclusive = false)
            viewModel.consumeOpenUrl()
        }
    }

    HistorySnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        topBar = {
            HistoryTopBar(
                searchQuery = state.searchQuery,
                hasEntries = state.entries.isNotEmpty(),
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSearchQueryClear = viewModel::onSearchQueryClear,
                onClearAllClick = viewModel::onClearAllRequested,
                onBackClick = { navController.popBackStack() },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        HistoryContent(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    val actionSheetTarget = state.pendingActionSheetTarget
    if (actionSheetTarget != null) {
        HistoryActionSheet(
            entry = actionSheetTarget,
            onOpenInNewTab = { viewModel.onOpenInNewTab(actionSheetTarget) },
            onDelete = { viewModel.onDeleteEntry(actionSheetTarget) },
            onCopyUrl = { viewModel.onCopyUrl(actionSheetTarget) },
            onDismiss = viewModel::onActionSheetDismiss,
        )
    }

    if (state.isClearAllDialogVisible) {
        ClearAllHistoryConfirmDialog(
            onConfirm = viewModel::onClearAllConfirmed,
            onCancel = viewModel::onClearAllCancelled,
        )
    }
}

/**
 * Spec 014 FR-009 / FR-015 / FR-027 — three mutually-exclusive list branches:
 * nothing recorded at all, nothing matching an active query, or the grouped list.
 */
@Composable
private fun HistoryContent(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val isSearchActive = state.searchQuery.length >= BrowserLimits.SEARCH_MIN_CHARS
    when {
        state.entries.isEmpty() -> EmptyHistoryState(modifier = modifier)

        isSearchActive && state.groupedEntries.isEmpty() ->
            NoHistoryMatchesState(query = state.searchQuery, modifier = modifier)

        else -> LazyColumn(modifier = modifier) {
            for (group in state.groupedEntries) {
                item(key = "header_${group.bucket}") {
                    HistoryDayHeader(bucket = group.bucket)
                }
                items(group.entries, key = { entry -> entry.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        faviconCache = viewModel.faviconCache,
                        onClick = { viewModel.onEntryTap(entry) },
                        onLongClick = { viewModel.onLongPressEntry(entry) },
                    )
                }
            }
        }
    }
}

/**
 * Spec 014 FR-019a / FR-020 / FR-021 — collects the one-shot snackbar channel and
 * resolves each event to its localized message. Kept in its own Composable so the
 * `stringResource` lookups stay inside composition while `collect` runs in an effect.
 */
@Composable
private fun HistorySnackbarEffect(
    viewModel: HistoryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val clipboardCopied = stringResource(R.string.history_snackbar_clipboard_copied)
    val tabCapReached = stringResource(R.string.history_snackbar_tab_cap_reached)
    val deletionFailed = stringResource(R.string.history_snackbar_deletion_failed)
    LaunchedEffect(Unit) {
        viewModel.historySnackbarEvent.collect { event ->
            val message = when (event) {
                HistoryErrorEvent.ClipboardCopied -> clipboardCopied
                HistoryErrorEvent.TabCapReached -> tabCapReached
                is HistoryErrorEvent.DeletionFailed -> deletionFailed
            }
            snackbarHostState.showSnackbar(message)
        }
    }
}
