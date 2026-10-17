@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabPreview
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.entrySign

internal const val TEST_TAG_TAB_SWIPE_LANE: String = "tab_swipe_lane"
internal const val TEST_TAG_TAB_SWIPE_GHOST: String = "tab_swipe_ghost"

/**
 * The address pill and, while a swipe is in progress, the incoming tab's pill beside it. Both move
 * in the layer only, so a drag recomposes neither.
 */
@Composable
internal fun TabSwipeLane(
    state: TabSwipeState,
    isIncognito: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier = modifier.clipToBounds().testTag(TEST_TAG_TAB_SWIPE_LANE)) {
        Box(modifier = Modifier.graphicsLayer { translationX = state.offsetPx }) { content() }
        val incoming = state.incoming
        val side = state.side
        if (incoming != null && side != null) {
            GhostPill(
                preview = incoming,
                isIncognito = isIncognito,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = state.offsetPx + side.entrySign(rtl) * size.width },
            )
        }
    }
}

@Composable
private fun GhostPill(preview: TabPreview, isIncognito: Boolean, modifier: Modifier = Modifier) {
    val colors = ThirtySixTheme.colors
    val fill = if (isIncognito) colors.glass else MaterialTheme.colorScheme.surfaceContainer
    val content = if (isIncognito) colors.onIncognito else MaterialTheme.colorScheme.onSurface
    val label = when {
        preview.isIncognito -> stringResource(R.string.tabs_card_incognito_label)
        preview.isHome -> stringResource(R.string.browser_action_home)
        else -> preview.host
    }
    Box(
        // Centred, so the label shows while only part of the pill has slid in.
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.searchWell)
            .background(fill, ThirtySixTheme.shapes.full)
            .padding(horizontal = Spacing.md)
            .testTag(TEST_TAG_TAB_SWIPE_GHOST),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
