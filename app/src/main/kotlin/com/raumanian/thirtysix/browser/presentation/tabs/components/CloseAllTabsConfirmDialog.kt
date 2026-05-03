@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

/**
 * Spec 011 (T045 / US3 / FR-012) — destructive-action confirmation dialog
 * for the "Close all tabs" affordance.
 *
 * Material 3 [AlertDialog] with title + body text + Confirm / Cancel buttons.
 * Confirm fires `onConfirm` (caller dispatches `TabsViewModel.onCloseAllConfirmed`);
 * Cancel + outside-dismiss fire `onDismiss` (caller dispatches `onCloseAllDismissed`).
 *
 * Strings localized via Spec 011 T003 keys
 * (`tabs_switcher_close_all`, `tabs_switcher_close_all_confirm`).
 */
@Composable
fun CloseAllTabsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.tabs_switcher_close_all))
        },
        text = {
            Text(text = stringResource(R.string.tabs_switcher_close_all_confirm))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = androidx.compose.ui.Modifier.testTag(TEST_TAG_CONFIRM_BUTTON),
            ) {
                Text(text = stringResource(R.string.tabs_switcher_close_all))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = androidx.compose.ui.Modifier.testTag(TEST_TAG_DISMISS_BUTTON),
            ) {
                // Reuse Android's standard "Cancel" string from the framework
                // — for v1.0 we tag with a project-local key. Mid-impl
                // refactor may move this into strings.xml if reused elsewhere.
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        modifier = androidx.compose.ui.Modifier.testTag(TEST_TAG_CLOSE_ALL_DIALOG),
    )
}

const val TEST_TAG_CLOSE_ALL_DIALOG: String = "tabs_close_all_dialog"
const val TEST_TAG_CONFIRM_BUTTON: String = "tabs_close_all_confirm"
const val TEST_TAG_DISMISS_BUTTON: String = "tabs_close_all_dismiss"
