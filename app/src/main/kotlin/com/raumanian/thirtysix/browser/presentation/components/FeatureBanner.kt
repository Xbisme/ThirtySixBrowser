@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.theme.fabGlow
import com.raumanian.thirtysix.browser.presentation.theme.softShadow

private val ChipletRadius = 12.dp
private val HeroChipRadius = 16.dp
private val StripPadding = 12.dp

/** Positions of the stage banner's floating chiplets (design system FeatureBanner preview). */
@Suppress("MagicNumber") // Offsets from the stage centre ARE the layout constants (design system preview).
private val Chiplets: List<Triple<Int, Dp, Dp>> = listOf(
    Triple(AppIcons.Cookie, (-112).dp, (-20).dp),
    Triple(AppIcons.History, (-60).dp, 24.dp),
    Triple(AppIcons.Lock, 108.dp, (-22).dp),
    Triple(AppIcons.Shield, 56.dp, 26.dp),
)

/**
 * Spec 019 — the hub's privacy banner (design system FeatureBanner, stage form): static icon
 * chiplets around a phone glyph over a `primaryContainer` strip carrying [text] and a chevron.
 * Used once, for the privacy promise; the whole banner is one button.
 */
@Composable
fun PrivacyStageBanner(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        BannerStage()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.primaryContainer)
                .padding(horizontal = Spacing.md, vertical = StripPadding),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(AppIcons.ChevronRight),
                null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }
    }
}

/** The stage half of [PrivacyStageBanner]: static chiplets around the phone glyph. */
@Composable
private fun BannerStage() {
    val scheme = MaterialTheme.colorScheme
    val colors = ThirtySixTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.bannerStage),
    ) {
        Chiplets.forEach { (icon, x, y) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x, y)
                    .size(Dimens.bannerChiplet)
                    .softShadow(RoundedCornerShape(ChipletRadius), colors.shadow)
                    .background(scheme.surfaceContainerLow, RoundedCornerShape(ChipletRadius)),
            ) {
                Icon(
                    painterResource(icon),
                    null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.bannerHeroChip)
                .softShadow(RoundedCornerShape(HeroChipRadius), colors.shadow)
                .background(scheme.primaryContainer, RoundedCornerShape(HeroChipRadius)),
        ) {
            Icon(
                painterResource(AppIcons.PhoneFilled),
                null,
                tint = scheme.primary,
                modifier = Modifier.size(Dimens.icon),
            )
        }
    }
}

/**
 * Spec 019 — the row banner (design system FeatureBanner): a kicker in the display face, one
 * line of explanation and a gradient coin. Used for "Browse without a trace".
 */
@Composable
fun RowBanner(
    title: String,
    body: String,
    @DrawableRes coinIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = ThirtySixTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                ),
                color = scheme.onSurface,
            )
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.bannerCoin)
                .fabGlow(ThirtySixTheme.shapes.full, colors.glow)
                .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)), ThirtySixTheme.shapes.full)
                .border(Dimens.hairline, colors.glassStroke, ThirtySixTheme.shapes.full),
        ) {
            Icon(painterResource(coinIcon), null, tint = scheme.onPrimary, modifier = Modifier.size(Dimens.iconLarge))
        }
    }
}
