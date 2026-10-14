@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.history.presentation.history.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
// Every dismissal route goes to [onCancel], so cancelling any way leaves history untouched.
@Composable
fun ClearAllHistoryConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmDialog(
        title = stringResource(R.string.history_clear_all_dialog_title),
        body = stringResource(R.string.history_clear_all_dialog_body),
        confirmLabel = stringResource(R.string.history_clear_all_dialog_confirm),
        cancelLabel = stringResource(R.string.history_clear_all_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onCancel,
        glyph = AppIcons.DeleteSweep,
        modifier = modifier.testTag(TEST_TAG_HISTORY_CLEAR_ALL_DIALOG),
    )
}

const val TEST_TAG_HISTORY_CLEAR_ALL_DIALOG: String = "history_clear_all_dialog"

// Not attached: [ConfirmDialog] has no button modifier, so tests find the confirm button by label.
const val TEST_TAG_HISTORY_CLEAR_ALL_CONFIRM: String = "history_clear_all_confirm"
