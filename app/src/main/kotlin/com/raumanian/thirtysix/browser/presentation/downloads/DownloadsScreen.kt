@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.downloads

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
import com.raumanian.thirtysix.browser.presentation.downloads.components.DeleteDownloadedFileConfirmDialog
import com.raumanian.thirtysix.browser.presentation.downloads.components.DownloadActionSheet
import com.raumanian.thirtysix.browser.presentation.downloads.components.DownloadActionSheetCallbacks
import com.raumanian.thirtysix.browser.presentation.downloads.components.DownloadRow
import com.raumanian.thirtysix.browser.presentation.downloads.components.DownloadsTopBar
import com.raumanian.thirtysix.browser.presentation.downloads.components.EmptyDownloadsState

/**
 * Spec 015 — the Downloads screen.
 *
 * Replaces the Spec 002 placeholder. Renders the list or the empty state, hosts the
 * long-press action sheet and the delete confirmation, and surfaces one-shot events.
 */
@Composable
fun DownloadsScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DownloadsSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DownloadsTopBar(onBackClick = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyDownloadsState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Stable key — Constitution §V forbids index-only keys, which would make
                // Compose animate a phantom move whenever a row is removed.
                items(items = state.items, key = { it.record.id }) { item ->
                    DownloadRow(
                        item = item,
                        onClick = { viewModel.onItemClicked(item) },
                        onLongClick = { viewModel.onItemLongPressed(item) },
                        onCancelClick = { viewModel.onCancelClicked(item) },
                    )
                }
            }
        }
    }

    state.pendingActionSheetTarget?.let { target ->
        DownloadActionSheet(
            item = target,
            callbacks = DownloadActionSheetCallbacks(
                onOpen = { viewModel.onItemClicked(target) },
                onCopyLink = { viewModel.onCopyLinkClicked(target) },
                onRemoveFromList = { viewModel.onRemoveFromListClicked(target) },
                onDeleteFile = { viewModel.onDeleteFileRequested(target.record) },
                onDismiss = viewModel::onActionSheetDismissed,
            ),
        )
    }

    state.pendingDeleteConfirmation?.let { record ->
        DeleteDownloadedFileConfirmDialog(
            record = record,
            onConfirm = { viewModel.onDeleteFileConfirmed(record) },
            onDismiss = viewModel::onDeleteConfirmationDismissed,
        )
    }
}

/**
 * Extracted so [DownloadsScreen] stays under detekt's `LongMethod` ceiling, and because
 * resolving eight strings inline would bury the layout it belongs to.
 */
@Composable
private fun DownloadsSnackbarEffect(
    viewModel: DownloadsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val linkCopied = stringResource(R.string.downloads_link_copied)
    val noAppCanOpen = stringResource(R.string.downloads_no_app_can_open)
    val fileMissing = stringResource(R.string.downloads_file_missing)
    val notComplete = stringResource(R.string.downloads_not_complete)
    val fileDeleted = stringResource(R.string.downloads_file_deleted)
    val recordRemoved = stringResource(R.string.downloads_record_removed)
    val cancelled = stringResource(R.string.downloads_cancelled)
    val failed = stringResource(R.string.downloads_operation_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                DownloadsEvent.LinkCopied -> linkCopied
                DownloadsEvent.NoAppCanOpenFile -> noAppCanOpen
                is DownloadsEvent.FileMissing -> fileMissing
                DownloadsEvent.DownloadNotComplete -> notComplete
                DownloadsEvent.FileDeleted -> fileDeleted
                DownloadsEvent.RecordRemoved -> recordRemoved
                DownloadsEvent.DownloadCancelled -> cancelled
                DownloadsEvent.OperationFailed -> failed
            }
            snackbarHostState.showSnackbar(message)
        }
    }
}
