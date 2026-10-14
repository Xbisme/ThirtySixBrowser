@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
private val LabelGap = 6.dp
private const val LABEL_MAX_LINES = 2

/**
 * Spec 022 (contracts C16) — optional state of a [QuickTile], defaulted so existing tiles are
 * unchanged.
 * - [selected]: when non-null the tile is a switch. It shows the primary container when on, and
 *   TalkBack reads it as a switch with an "On" / "Off" state (FR-003, FR-036).
 * - [value]: a second line under the label, for example the current text size "125%".
 */
@Immutable
data class QuickTileState(val selected: Boolean? = null, val value: String? = null)

/**
 * Spec 019 — the hub's tiles (design system QuickTile): an icon well with the label underneath.
 * [QuickTileTone.Place] tiles are white with a hairline ring; pastel tiles are square and use a
 * filled icon in their matching ink. Labels wrap to two lines and hyphenate a single long word
 * rather than break it at an arbitrary character; a label that still splits badly gets a shorter
 * hub-specific string (`hub_tile_search_engine`, German "Suche"). Spec 022 adds [state].
 */
@Composable
@Suppress("LongParameterList") // Design-system tile: label, icon, tone, action, modifier, state.
fun QuickTile(
    label: String,
    @DrawableRes icon: Int,
    tone: QuickTileTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: QuickTileState = QuickTileState(),
) {
    val shape = MaterialTheme.shapes.large
    val colors = quickTileColors(tone, on = state.selected == true)
    val selected = state.selected
    val stateText = selected?.let {
        stringResource(if (it) R.string.hub_tile_state_on else R.string.hub_tile_state_off)
    }
    val interaction = if (selected == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
            .toggleable(value = selected, role = Role.Switch, onValueChange = { onClick() })
            .semantics { stateText?.let { stateDescription = it } }
    }
    val wellSize = if (tone == QuickTileTone.Place) Modifier.height(Dimens.tileWell) else Modifier.aspectRatio(1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LabelGap),
        modifier = modifier
            .clip(shape)
            .then(interaction),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(wellSize)
                .clip(shape)
                .background(colors.fill)
                .border(width = Dimens.hairline, color = colors.ring, shape = shape),
        ) {
            Icon(painterResource(icon), null, tint = colors.ink, modifier = Modifier.size(Dimens.icon))
        }
        QuickTileLabels(label = label, value = state.value)
    }
}

/** The label, wrapping to two lines (FR-055), and the optional value line under it. */
@Composable
private fun QuickTileLabels(label: String, value: String?) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = LABEL_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        // Fill the tile so a long label wraps instead of being clipped (FR-055).
        modifier = Modifier.fillMaxWidth(),
    )
    if (value != null) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class QuickTileColors(val fill: Color, val ink: Color, val ring: Color)

/** The well's fill, icon ink and ring for a tone; a switched-on tile uses the primary container. */
@Composable
private fun quickTileColors(tone: QuickTileTone, on: Boolean): QuickTileColors {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    if (on) return QuickTileColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    return when (tone) {
        QuickTileTone.Place -> QuickTileColors(scheme.surfaceContainerLow, scheme.onSurface, scheme.outlineVariant)
        QuickTileTone.Sky -> QuickTileColors(colors.tileSky, colors.tileSkyInk, Color.Transparent)
        QuickTileTone.Sun -> QuickTileColors(colors.tileSun, colors.tileSunInk, Color.Transparent)
        QuickTileTone.Mint -> QuickTileColors(colors.tileMint, colors.tileMintInk, Color.Transparent)
        QuickTileTone.Peach -> QuickTileColors(colors.tilePeach, colors.tilePeachInk, Color.Transparent)
    }
}
