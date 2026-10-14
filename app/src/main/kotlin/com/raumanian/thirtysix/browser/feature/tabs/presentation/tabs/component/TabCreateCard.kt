@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.TabsMode
private const val DISABLED_ALPHA = 0.5f

/**
 * Spec 019 FR-040 — the one "create" card at the end of the grid, for the mode on screen: a
 * new tab in Open mode, a new incognito tab (on the plum surface) in Incognito mode. Same
 * height as a [TabSwitcherCard]. At the mode's tab cap it dims and stops responding
 * (Spec 011 FR-016, Spec 012 FR-004). Replaces Spec 011/012's two separate cards.
 */
@Composable
fun TabCreateCard(
    mode: TabsMode,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val incognito = mode == TabsMode.Incognito
    val (label, tag) = if (incognito) {
        stringResource(R.string.tabs_action_new_incognito_tab) to TEST_TAG_NEW_INCOGNITO_TAB_CARD
    } else {
        stringResource(R.string.tabs_switcher_new_tab) to TEST_TAG_NEW_TAB_CARD
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.tabCardHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(if (incognito) colors.surfaceIncognito else scheme.surfaceContainerLow)
            .border(Dimens.hairline, if (incognito) colors.glassStroke else scheme.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.fab)
                .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)), ThirtySixTheme.shapes.full),
        ) {
            Icon(
                painter = painterResource(if (incognito) AppIcons.IncognitoFilled else AppIcons.Add),
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(Dimens.icon),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (incognito) colors.onIncognito else scheme.onSurface,
        )
    }
}

const val TEST_TAG_NEW_TAB_CARD: String = "tabs_new_tab_card"
const val TEST_TAG_NEW_INCOGNITO_TAB_CARD: String = "tabs_new_incognito_tab_card"
