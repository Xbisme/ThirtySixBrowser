@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
/**
 * Spec 012 US5 — confirm before closing every incognito tab ("Close all 3 incognito tabs?");
 * Spec 019 FR-045 restyle on [ConfirmDialog].
 */
@Composable
fun CloseAllIncognitoConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmDialog(
        title = stringResource(R.string.tabs_action_close_all_incognito),
        body = stringResource(R.string.tabs_close_all_incognito_confirm, count),
        confirmLabel = stringResource(R.string.tabs_action_close_all_incognito),
        cancelLabel = stringResource(android.R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        glyph = AppIcons.IncognitoFilled,
        modifier = modifier.testTag(TEST_TAG_CLOSE_ALL_INCOGNITO_DIALOG),
    )
}

const val TEST_TAG_CLOSE_ALL_INCOGNITO_DIALOG: String = "tabs_close_all_incognito_dialog"
