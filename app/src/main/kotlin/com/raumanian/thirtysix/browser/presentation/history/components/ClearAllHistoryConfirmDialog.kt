@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

/**
 * Spec 014 FR-024 / FR-026 — confirmation gate in front of the irreversible
 * clear-all wipe. The confirm button is error-tinted to signal the destructive
 * outcome; `onDismissRequest` (tap-outside / system back) routes to [onCancel]
 * so cancelling by any route leaves history untouched.
 */
@Composable
fun ClearAllHistoryConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(TEST_TAG_HISTORY_CLEAR_ALL_DIALOG),
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.history_clear_all_dialog_title)) },
        text = { Text(stringResource(R.string.history_clear_all_dialog_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TEST_TAG_HISTORY_CLEAR_ALL_CONFIRM),
            ) {
                Text(
                    text = stringResource(R.string.history_clear_all_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.history_clear_all_dialog_cancel))
            }
        },
    )
}

/** Test tags exposed for instrumented tests driving the clear-all flow. */
const val TEST_TAG_HISTORY_CLEAR_ALL_DIALOG: String = "history_clear_all_dialog"
const val TEST_TAG_HISTORY_CLEAR_ALL_CONFIRM: String = "history_clear_all_confirm"
