@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
private const val DISABLED_ALPHA = 0.38f

/**
 * Spec 019 — the design system's pill button. One filled button per view at most; danger
 * buttons name their action ("Clear all"), never "OK". No fixed width: labels must fit
 * German and Russian (FR-055).
 */
@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = ThirtySixTheme.colors
    val shape = ThirtySixTheme.shapes.full
    val content = when (style) {
        PillStyle.Filled, PillStyle.Gradient -> scheme.onPrimary
        PillStyle.Tonal -> scheme.onPrimaryContainer
        PillStyle.Outline -> scheme.onSurface
        PillStyle.Text -> scheme.primary
        PillStyle.Danger -> scheme.error
        PillStyle.DangerFilled -> scheme.onError
        PillStyle.Raised -> scheme.onSurface
        PillStyle.Glass -> colors.onGlass
    }
    val fill: Modifier = when (style) {
        PillStyle.Filled -> Modifier.background(scheme.primary, shape)
        PillStyle.Gradient -> Modifier.background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)), shape)
        PillStyle.Tonal -> Modifier.background(scheme.primaryContainer, shape)
        PillStyle.Outline -> Modifier.border(Dimens.hairline, scheme.outlineVariant, shape)
        PillStyle.Text, PillStyle.Danger -> Modifier
        PillStyle.DangerFilled -> Modifier.background(scheme.error, shape)
        PillStyle.Raised -> Modifier.background(scheme.surfaceContainerLow, shape)
        PillStyle.Glass -> Modifier.background(colors.glass, shape).border(Dimens.hairline, colors.glassStroke, shape)
    }
    val horizontal = if (style == PillStyle.Text || style == PillStyle.Danger) Spacing.md else Spacing.lg
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = Dimens.button)
            .clip(shape)
            .then(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontal)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), null, tint = content, modifier = Modifier.size(Dimens.iconSmall))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
        if (trailingIcon != null) {
            Icon(painterResource(trailingIcon), null, tint = content, modifier = Modifier.size(Dimens.iconSmall))
        }
    }
}
