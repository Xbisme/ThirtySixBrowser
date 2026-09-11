@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention

/**
 * Spec 016 US5 FR-021 — the warning before a shorter retention window deletes history.
 *
 * The body names the new window, so the user sees exactly what will go. Confirm is error-tinted
 * because the deletion cannot be undone; an outside tap, system back and Cancel all route to
 * [onCancel], which leaves both the window and the history untouched.
 */
@Composable
fun ConfirmShortenRetentionDialog(
    target: HistoryRetention,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG),
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.settings_retention_shorten_title)) },
        text = {
            Text(text = stringResource(R.string.settings_retention_shorten_body, historyRetentionLabel(target)))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(TEST_TAG_SETTINGS_RETENTION_CONFIRM),
            ) {
                Text(text = stringResource(R.string.settings_retention_shorten_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(TEST_TAG_SETTINGS_RETENTION_CANCEL)) {
                Text(text = stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

const val TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG: String = "settings_retention_confirm_dialog"
const val TEST_TAG_SETTINGS_RETENTION_CONFIRM: String = "settings_retention_confirm"
const val TEST_TAG_SETTINGS_RETENTION_CANCEL: String = "settings_retention_cancel"
