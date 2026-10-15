@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
internal const val TEST_TAG_DELETE_FILE_DIALOG: String = "download_delete_file_dialog"

/**
 * The body names the file: "delete this file?" with several similar downloads on screen is not
 * answerable. Tests find the confirm button by label: [ConfirmDialog] exposes no button modifier.
 */
@Composable
fun DeleteDownloadedFileConfirmDialog(
    record: DownloadRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmDialog(
        title = stringResource(R.string.downloads_delete_dialog_title),
        body = stringResource(R.string.downloads_delete_dialog_body, record.fileName),
        confirmLabel = stringResource(R.string.downloads_delete_dialog_confirm),
        cancelLabel = stringResource(R.string.downloads_delete_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_DELETE_FILE_DIALOG),
    )
}
