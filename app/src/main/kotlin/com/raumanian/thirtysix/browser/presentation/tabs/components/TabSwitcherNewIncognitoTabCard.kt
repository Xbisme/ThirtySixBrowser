@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 012 (T029 / FR-001) — "new incognito tab" card for the switcher.
 *
 * Mirrors [TabSwitcherNewTabCard] zone-for-zone (16:9 preview area + title row
 * with leading icon + label + invisible 48dp trailing IconButton spacer) so
 * normal and incognito new-tab cards have identical card height when rendered
 * side-by-side in the grid.
 *
 * Visual differentiation (FR-013):
 *  - Background colour: [MaterialTheme.colorScheme.surfaceVariant] (preview
 *    area) and [MaterialTheme.colorScheme.tertiaryContainer] for the leading
 *    glyph circle. Distinct from [TabSwitcherNewTabCard]'s `primaryContainer`.
 *  - Glyph: [Icons.Filled.Lock] — closest M3 stable icon for "private/incognito"
 *    semantic without pulling the larger material-icons-extended bundle.
 *
 * a11y: glyph carries `tabs_a11y_incognito_glyph` content description (FR-025).
 */
@Composable
fun TabSwitcherNewIncognitoTabCard(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11yLabel = stringResource(R.string.tabs_a11y_new_incognito_tab)
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) ENABLED_ALPHA else DISABLED_ALPHA)
            .semantics { contentDescription = a11yLabel }
            .testTag(TEST_TAG_NEW_INCOGNITO_TAB_CARD),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xs),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IncognitoNewTabPreviewArea()
            IncognitoNewTabTitleRow()
        }
    }
}

@Composable
private fun IncognitoNewTabPreviewArea() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(NEW_TAB_PLACEHOLDER_ASPECT_RATIO)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IncognitoNewTabTitleRow() {
    val cardLabel = stringResource(R.string.tabs_action_new_incognito_tab)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(LEADING_ICON_SIZE_DP)
                .background(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(LEADING_ICON_INNER_SIZE_DP),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cardLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = " ",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.alpha(0f),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
            )
        }
    }
}

const val TEST_TAG_NEW_INCOGNITO_TAB_CARD: String = "tabs_new_incognito_tab_card"

private const val ENABLED_ALPHA: Float = 1f
private const val DISABLED_ALPHA: Float = 0.5f
private const val NEW_TAB_PLACEHOLDER_ASPECT_RATIO: Float = 16f / 9f
private val LEADING_ICON_SIZE_DP = 16.dp
private val LEADING_ICON_INNER_SIZE_DP = 12.dp
