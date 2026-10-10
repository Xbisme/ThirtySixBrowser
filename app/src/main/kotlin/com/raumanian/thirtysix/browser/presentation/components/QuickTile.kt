@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

private val LabelGap = 6.dp
private const val LABEL_MAX_LINES = 2

/**
 * Spec 019 — the hub's tiles (design system QuickTile): an icon well with the label underneath.
 * [QuickTileTone.Place] tiles are white with a hairline ring; pastel tiles are square and use a
 * filled icon in their matching ink. Labels wrap to two lines (German "Suchmaschine").
 */
@Composable
fun QuickTile(
    label: String,
    @DrawableRes icon: Int,
    tone: QuickTileTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val (fill, ink) = when (tone) {
        QuickTileTone.Place -> scheme.surfaceContainerLow to scheme.onSurface
        QuickTileTone.Sky -> colors.tileSky to colors.tileSkyInk
        QuickTileTone.Sun -> colors.tileSun to colors.tileSunInk
        QuickTileTone.Mint -> colors.tileMint to colors.tileMintInk
        QuickTileTone.Peach -> colors.tilePeach to colors.tilePeachInk
    }
    val wellSize = if (tone == QuickTileTone.Place) Modifier.height(Dimens.tileWell) else Modifier.aspectRatio(1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LabelGap),
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(wellSize)
                .clip(shape)
                .background(fill)
                .border(
                    width = Dimens.hairline,
                    color = if (tone == QuickTileTone.Place) scheme.outlineVariant else Color.Transparent,
                    shape = shape,
                ),
        ) {
            Icon(painterResource(icon), null, tint = ink, modifier = Modifier.size(Dimens.icon))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = LABEL_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            // Fill the tile so a long label wraps instead of being clipped (FR-055).
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
