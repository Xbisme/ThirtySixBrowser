@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.DeleteDownloadedFileConfirmDialog
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.DownloadActionSheet
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.DownloadActionSheetCallbacks
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.DownloadRow
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.DownloadsTopBar
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.EmptyDownloadsState
private const val DOWNLOAD_ROW_CONTENT_TYPE = "download_row"

@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DownloadsSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
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
                // Stable keys: index keys would make Compose animate a phantom move whenever a row is removed.
                items(
                    items = state.items,
                    key = { it.record.id },
                    contentType = { DOWNLOAD_ROW_CONTENT_TYPE },
                ) { item ->
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
    val removeLabel = stringResource(R.string.downloads_action_remove)

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
            // The stale row must be removable from the same message that says the file is gone.
            val stale = event as? DownloadsEvent.FileMissing
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = stale?.let { removeLabel },
            )
            if (stale != null && result == SnackbarResult.ActionPerformed) {
                viewModel.onRemoveFromListClicked(stale.item)
            }
        }
    }
}
