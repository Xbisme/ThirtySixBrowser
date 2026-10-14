@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
internal const val TEST_TAG_FIRE_CONFIRM: String = "fire_confirm_dialog"

/**
 * Spec 024 FR-002 — the confirmation every Fire run asks for: what is cleared and what is kept.
 * Cancel and Back dismiss it and change nothing (US1 #2).
 */
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
