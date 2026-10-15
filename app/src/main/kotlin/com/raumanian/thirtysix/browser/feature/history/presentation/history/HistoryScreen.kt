@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.history.presentation.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.ClearAllHistoryConfirmDialog
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.EmptyHistoryState
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.HistoryActionSheet
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.HistoryDayHeader
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.HistoryRow
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.HistoryTopBar
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.NoHistoryMatchesState

// `FaviconCache` comes through the ViewModel so instrumented tests can swap in a no-op fake.
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: HistoryViewModel = hiltViewModel(),
    onShowBrowser: () -> Unit = {},
) {
    val showBrowser by rememberUpdatedState(onShowBrowser)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // After a tap-replace or open-in-new-tab, pop back so the page loads in front of the user.
    LaunchedEffect(state.openUrl) {
        if (state.openUrl != null) {
            showBrowser()
            viewModel.consumeOpenUrl()
        }
    }

    HistorySnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        modifier = modifier,
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
        containerColor = MaterialTheme.colorScheme.surface,
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

// Headers and rows recycle only among their own kind.
private const val CONTENT_TYPE_DAY_HEADER = "history_day_header"
private const val CONTENT_TYPE_ROW = "history_row"

// Three exclusive branches: nothing recorded, nothing matching the query, or the grouped list.
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
                item(key = "header_${group.bucket}", contentType = CONTENT_TYPE_DAY_HEADER) {
                    HistoryDayHeader(bucket = group.bucket)
                }
                items(
                    items = group.entries,
                    key = { entry -> entry.id },
                    contentType = { CONTENT_TYPE_ROW },
                ) { entry ->
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

// Its own Composable so `stringResource` lookups stay in composition while `collect` runs in an effect.
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
