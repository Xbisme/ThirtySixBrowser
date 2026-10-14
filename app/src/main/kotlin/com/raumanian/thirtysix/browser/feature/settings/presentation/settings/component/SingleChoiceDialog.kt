@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ChoiceDialog
/**
 * Spec 016 — the shared body of every Settings chooser: a titled list of mutually exclusive
 * options, one of them selected. Spec 019 draws it with the design system's [ChoiceDialog]
 * (selected row tinted, no OK button — FR-045).
 *
 * Choosing an option reports it through [onSelect] and nothing else — the caller decides what
 * happens next, which is what lets the public choosers stay stateless for Spec 018 (FR-037).
 * Each option is one `selectable` row with the radio-button role, so TalkBack announces it with
 * its selected state (FR-039). The options scroll, so nine languages still fit a small screen in
 * landscape. An outside tap, system back and Cancel all route to [onDismiss].
 */
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
