@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.privacy.presentation.fire

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
internal const val TEST_TAG_FIRE_CONFIRM: String = "fire_confirm_dialog"

/** What every Fire run clears and keeps; Cancel and Back change nothing. */
@Composable
internal fun FireConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.fire_confirm_title),
        body = stringResource(R.string.fire_confirm_body),
        confirmLabel = stringResource(R.string.fire_confirm_action),
        cancelLabel = stringResource(R.string.fire_confirm_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        glyph = AppIcons.Fire,
        destructive = true,
        modifier = Modifier.testTag(TEST_TAG_FIRE_CONFIRM),
    )
}
