@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

private val OptionPadding = 14.dp
private val OptionMinHeight = 52.dp
private val OptionGap = 4.dp

/**
 * Spec 019 — single-choice dialog (design system Dialog, choice form). Options are rounded rows;
 * the selected row is tinted `primaryContainer` with a check. Tapping an option reports it via
 * [onSelect] — there is no OK button (FR-045). Each row is a `selectable` radio for TalkBack.
 * Back, an outside tap and [cancelLabel] all route to [onDismiss].
 */
@Composable
@Suppress("LongParameterList")
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String,
    modifier: Modifier = Modifier,
    optionLeading: (@Composable (T) -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier.widthIn(max = Dimens.dialogMaxWidth),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(OptionGap),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .selectableGroup()
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        ChoiceRow(
                            label = optionLabel(option),
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            leading = optionLeading?.let { { it(option) } },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton(label = cancelLabel, onClick = onDismiss, style = PillStyle.Text)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OptionPadding),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OptionMinHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = OptionPadding),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(painterResource(AppIcons.Check), null, tint = scheme.primary, modifier = Modifier.size(Dimens.icon))
        }
    }
}
