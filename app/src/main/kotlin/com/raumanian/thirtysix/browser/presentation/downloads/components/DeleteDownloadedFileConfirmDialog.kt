@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.downloads.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord

internal const val TEST_TAG_DELETE_FILE_DIALOG: String = "download_delete_file_dialog"
internal const val TEST_TAG_DELETE_FILE_CONFIRM: String = "download_delete_file_confirm"

/**
 * Spec 015 FR-032 — deleting a file is irreversible, so it always goes through this.
 *
 * The body names the file, because "delete this file?" with three similarly-named downloads
 * on screen is not a question a user can answer confidently. Mirrors Spec 014's
 * `ClearAllHistoryConfirmDialog`, including the destructive tint on the confirm action.
 */
@Composable
fun DeleteDownloadedFileConfirmDialog(
    record: DownloadRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TEST_TAG_DELETE_FILE_DIALOG),
        title = { Text(stringResource(R.string.downloads_delete_dialog_title)) },
        text = { Text(stringResource(R.string.downloads_delete_dialog_body, record.fileName)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(TEST_TAG_DELETE_FILE_CONFIRM)) {
                Text(
                    text = stringResource(R.string.downloads_delete_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_delete_dialog_cancel))
            }
        },
    )
}
