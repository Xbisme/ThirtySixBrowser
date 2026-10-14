@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
/**
 * Spec 016 US5 FR-021 — the warning before a shorter retention window deletes history.
 *
 * The body names the new window, so the user sees exactly what will go. Drawn with the design
 * system's [ConfirmDialog] (Spec 019 FR-045): the confirm button is a full-width danger button
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
    ConfirmDialog(
        title = stringResource(R.string.settings_retention_shorten_title),
        body = stringResource(R.string.settings_retention_shorten_body, historyRetentionLabel(target)),
        confirmLabel = stringResource(R.string.settings_retention_shorten_confirm),
        cancelLabel = stringResource(R.string.settings_action_cancel),
        onConfirm = onConfirm,
        onDismiss = onCancel,
        glyph = AppIcons.DeleteSweep,
        modifier = modifier.testTag(TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG),
    )
}

const val TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG: String = "settings_retention_confirm_dialog"
