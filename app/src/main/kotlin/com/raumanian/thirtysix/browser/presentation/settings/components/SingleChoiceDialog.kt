@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 016 — the shared body of every Settings chooser: a titled list of mutually exclusive
 * options, one of them selected.
 *
 * Choosing an option reports it through [onSelect] and nothing else — the caller decides what
 * happens next, which is what lets the public choosers stay stateless for Spec 018 (FR-037).
 * Each option is one `selectable` row with [Role.RadioButton], so TalkBack announces it as a
 * radio button with its selected state, and the row is at least the minimum interactive size
 * tall (FR-039). The options scroll, so nine languages still fit a small screen in landscape.
 * An outside tap, system back and Cancel all route to [onDismiss].
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
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    SingleChoiceOptionRow(
                        label = optionLabel(option),
                        isSelected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        // Selecting an option is the confirmation; the only button needed is a way out.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

@Composable
private fun SingleChoiceOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .testTag(TEST_TAG_SETTINGS_CHOICE_OPTION)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Visual only — the row carries the click and the semantics.
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Every option row in every chooser carries this tag, so tests can count and pick options. */
const val TEST_TAG_SETTINGS_CHOICE_OPTION: String = "settings_choice_option"
