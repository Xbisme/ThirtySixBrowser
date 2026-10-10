@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

private val ChipPaddingHorizontal = 14.dp
private val ChipIconGap = 6.dp

/**
 * Spec 019 — the design system's Chip: a pill with a hairline ring; when [selected] it turns
 * `primaryContainer` with its leading icon. Announced as a selectable tab-like option.
 */
@Composable
fun ThirtySixChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = ThirtySixTheme.shapes.full
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipIconGap),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(Dimens.chip)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerLow)
            .border(Dimens.hairline, if (selected) Color.Transparent else scheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = ChipPaddingHorizontal),
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), null, tint = content, modifier = Modifier.size(Dimens.iconSmall))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = if (leadingIcon != null) Spacing.xs else 0.dp),
        )
    }
}
