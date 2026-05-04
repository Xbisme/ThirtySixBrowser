@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

/**
 * Spec 012 (T060 / US5 / FR-011) — destructive-action confirmation dialog
 * for the "Close all incognito" affordance.
 *
 * Mirrors [CloseAllTabsConfirmDialog] structure (M3 [AlertDialog] with title
 * + body text + Confirm / Cancel buttons). Body text passes [count] through
 * the `tabs_close_all_incognito_confirm` localized format string so users
 * see "Close all 3 incognito tabs?" (or the locale equivalent).
 */
@Composable
fun CloseAllIncognitoConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.tabs_action_close_all_incognito))
        },
        text = {
            Text(text = stringResource(R.string.tabs_close_all_incognito_confirm, count))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TEST_TAG_INCOGNITO_CONFIRM_BUTTON),
            ) {
                Text(text = stringResource(R.string.tabs_action_close_all_incognito))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TEST_TAG_INCOGNITO_DISMISS_BUTTON),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        modifier = Modifier.testTag(TEST_TAG_CLOSE_ALL_INCOGNITO_DIALOG),
    )
}

const val TEST_TAG_CLOSE_ALL_INCOGNITO_DIALOG: String = "tabs_close_all_incognito_dialog"
const val TEST_TAG_INCOGNITO_CONFIRM_BUTTON: String = "tabs_close_all_incognito_confirm"
const val TEST_TAG_INCOGNITO_DISMISS_BUTTON: String = "tabs_close_all_incognito_dismiss"
