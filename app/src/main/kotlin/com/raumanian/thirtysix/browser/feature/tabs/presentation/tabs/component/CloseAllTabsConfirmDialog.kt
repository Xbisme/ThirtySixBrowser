@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
/** Confirms before closing every normal tab. */
@Composable
fun CloseAllTabsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmDialog(
        title = stringResource(R.string.tabs_switcher_close_all),
        body = stringResource(R.string.tabs_switcher_close_all_confirm),
        confirmLabel = stringResource(R.string.tabs_switcher_close_all),
        cancelLabel = stringResource(android.R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_CLOSE_ALL_DIALOG),
    )
}

const val TEST_TAG_CLOSE_ALL_DIALOG: String = "tabs_close_all_dialog"
