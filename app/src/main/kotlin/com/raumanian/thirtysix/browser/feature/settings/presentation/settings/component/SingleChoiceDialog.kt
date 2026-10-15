@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ChoiceDialog
// The options scroll, so nine languages still fit a small screen in landscape.
@Composable
@Suppress("LongParameterList")
internal fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceDialog(
        title = title,
        options = options,
        selected = selected,
        optionLabel = optionLabel,
        onSelect = onSelect,
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.settings_action_cancel),
        modifier = modifier,
    )
}
