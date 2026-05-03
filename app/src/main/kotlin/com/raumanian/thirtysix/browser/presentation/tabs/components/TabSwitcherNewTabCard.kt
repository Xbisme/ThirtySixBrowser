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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 011 Q4 amendment (2026-05-03) — "new tab" card for the switcher
 * grid (FR-011), redesigned to occupy IDENTICAL screen space as
 * [TabSwitcherCard].
 *
 * Layout (top → bottom; matches [TabSwitcherCard] zone-for-zone):
 *  - 16:9 preview area: `primaryContainer` background with a centered "+"
 *    icon. Same aspect ratio as [TabSwitcherCard]'s preview so both cards
 *    have the SAME preview height when rendered side-by-side.
 *  - Title row: same height as [TabSwitcherCard]'s title row — leading
 *    16dp accent dot + label + invisible 48dp icon spacer (matches the
 *    space the close × `IconButton` occupies on a regular tab card so
 *    `TextOverflow.Ellipsis` cuts at the same position).
 *
 * Click always fires `onClick` — the cap-reached message surfacing happens
 * in the consumer (`TabsScreen` + `TabsViewModel.onNewTabClick`); this card
 * just dims to `0.5f` alpha when [enabled] is false (FR-016 visual disable).
 */
@Composable
fun TabSwitcherNewTabCard(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) ENABLED_ALPHA else DISABLED_ALPHA)
            .testTag(TEST_TAG_NEW_TAB_CARD),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xs),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NewTabPreviewArea()
            NewTabTitleRow()
        }
    }
}

@Composable
private fun NewTabPreviewArea() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(NEW_TAB_PLACEHOLDER_ASPECT_RATIO)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Mirrors `TabSwitcherCard.TabTitleRow` geometry so both cards have the
 * same overall height: 16dp leading icon slot + label column (2 lines:
 * title + invisible hostname placeholder) + 48dp trailing IconButton spacer.
 */
@Composable
private fun NewTabTitleRow() {
    val cardLabel = stringResource(R.string.tabs_switcher_new_tab)
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
                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                imageVector = Icons.Filled.Add,
                contentDescription = null,
            )
        }
    }
}

const val TEST_TAG_NEW_TAB_CARD: String = "tabs_new_tab_card"

private const val ENABLED_ALPHA: Float = 1f
private const val DISABLED_ALPHA: Float = 0.5f
private const val NEW_TAB_PLACEHOLDER_ASPECT_RATIO: Float = 16f / 9f
private val LEADING_ICON_SIZE_DP = 16.dp
private val LEADING_ICON_INNER_SIZE_DP = 12.dp
