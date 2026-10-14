@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.downloads.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
internal const val TEST_TAG_DELETE_FILE_DIALOG: String = "download_delete_file_dialog"

/**
 * Spec 015 FR-032 · Spec 019 FR-045 — deleting a file is irreversible, so it always goes
 * through this destructive [ConfirmDialog]: the body names the file (what is lost), and the
 * full-width action names the action above Cancel.
 *
 * The body names the file because "delete this file?" with three similarly-named downloads on
 * screen is not a question a user can answer confidently.
 *
 * The confirm button is found in tests by its label inside [TEST_TAG_DELETE_FILE_DIALOG]:
 * the shared [ConfirmDialog] exposes no modifier for its action button.
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
