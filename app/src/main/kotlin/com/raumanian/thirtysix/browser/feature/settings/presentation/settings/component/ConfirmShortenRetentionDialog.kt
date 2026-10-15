@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
// The deletion cannot be undone, so confirm is a danger button and every other way out cancels.
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
