@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
/**
 * Spec 014 FR-024 / FR-026 — confirmation gate in front of the irreversible clear-all wipe,
 * drawn as the shared destructive [ConfirmDialog] (Spec 019 FR-045): the body names what is
 * lost and the full-width error-filled action sits above Cancel. Dismissal (tap-outside /
 * system back) routes to [onCancel], so cancelling by any route leaves history untouched.
 */
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

/** Test tags exposed for instrumented tests driving the clear-all flow. */
const val TEST_TAG_HISTORY_CLEAR_ALL_DIALOG: String = "history_clear_all_dialog"

/**
 * Kept for source compatibility. [ConfirmDialog] exposes no per-button modifier, so this tag
 * is no longer attached; tests find the confirm button by its label inside
 * [TEST_TAG_HISTORY_CLEAR_ALL_DIALOG].
 */
const val TEST_TAG_HISTORY_CLEAR_ALL_CONFIRM: String = "history_clear_all_confirm"
