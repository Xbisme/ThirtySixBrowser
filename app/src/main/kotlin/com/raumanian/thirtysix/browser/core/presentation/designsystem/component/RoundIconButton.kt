@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
private const val DISABLED_ALPHA = 0.38f

/**
 * Round icon button: 44dp visual, 48dp touch target, always a spoken [contentDescription].
 * Disabled buttons stay in place at reduced opacity so bars never shift.
 */
@Composable
@Suppress("LongParameterList")
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RoundIconStyle = RoundIconStyle.Ringed,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (fill, ring, iconTint) = when (style) {
        RoundIconStyle.Ringed -> Triple(scheme.surfaceContainerLow, scheme.outlineVariant, scheme.onSurface)
        RoundIconStyle.Bare -> Triple(Color.Transparent, Color.Transparent, LocalContentColor.current)
        RoundIconStyle.Glass -> Triple(colors.glass, colors.glassStroke, colors.onGlass)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(Dimens.iconButton)
            .clip(ThirtySixTheme.shapes.full)
            .background(fill)
            .border(Dimens.hairline, ring, ThirtySixTheme.shapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (tint == Color.Unspecified) iconTint else tint,
            modifier = Modifier.size(Dimens.icon),
        )
    }
}
