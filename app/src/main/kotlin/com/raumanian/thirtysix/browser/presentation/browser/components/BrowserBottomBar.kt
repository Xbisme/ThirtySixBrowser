@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.theme.barShadow
import com.raumanian.thirtysix.browser.presentation.theme.fabGlow

internal const val TEST_TAG_NAV_HOME: String = "nav_home"
internal const val TEST_TAG_NAV_SEARCH: String = "nav_search"
internal const val TEST_TAG_NAV_NEW_TAB: String = "nav_new_tab"
internal const val TEST_TAG_NAV_TABS: String = "nav_tabs"
internal const val TEST_TAG_NAV_MENU: String = "nav_menu"

private val TabCountRadius = 7.dp
private val TabCountStroke = 1.6.dp
private const val TAB_COUNT_FONT_SP = 11
private val SlotLabelGap = 4.dp

/**
 * Spec 019 — the browser's bottom bar (design system BottomBar): Home · Search · raised
 * gradient New tab · Tabs (count drawn in a rounded square) · Menu (FR-020). There is no Back
 * slot: back is the system gesture (FR-021).
 *
 * The bar pads itself by the navigation-bar inset (FR-008). It slides away on page scroll, but
 * that movement is applied by the caller in `graphicsLayer { }` from `ChromeScrollState`, so
 * nothing here takes a moving value and scrolling never recomposes the bar (contracts C6).
 */
@Composable
internal fun BrowserBottomBar(
    tabCount: Int,
    isIncognito: Boolean,
    isHomeActive: Boolean,
    callbacks: BrowserBottomBarCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = ThirtySixTheme.shapes.bar
    val background = if (isIncognito) colors.surfaceIncognito else scheme.surfaceContainerLow
    val muted = if (isIncognito) colors.onIncognito else scheme.onSurfaceVariant
    val active = if (isIncognito) colors.onIncognito else scheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .barShadow(shape, colors.shadow)
            .clip(shape)
            .background(background)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBar)
                .padding(horizontal = Spacing.sm),
        ) {
            BarSlot(
                icon = if (isHomeActive) AppIcons.HomeFilled else AppIcons.Home,
                label = stringResource(R.string.browser_action_home),
                tint = if (isHomeActive) active else muted,
                onClick = callbacks.onHome,
                modifier = Modifier.weight(1f).testTag(TEST_TAG_NAV_HOME),
            )
            BarSlot(
                icon = AppIcons.Search,
                label = stringResource(R.string.nav_search),
                tint = muted,
                onClick = callbacks.onSearch,
                modifier = Modifier.weight(1f).testTag(TEST_TAG_NAV_SEARCH),
            )
            NewTabAction(onClick = callbacks.onNewTab, modifier = Modifier.weight(1f))
            TabsSlot(
                count = tabCount,
                tint = muted,
                onClick = callbacks.onTabs,
                onLongClick = callbacks.onTabsLongPress,
                modifier = Modifier.weight(1f),
            )
            BarSlot(
                icon = AppIcons.Menu,
                label = stringResource(R.string.nav_menu),
                tint = muted,
                onClick = callbacks.onMenu,
                modifier = Modifier.weight(1f).testTag(TEST_TAG_NAV_MENU),
            )
        }
    }
}

@Composable
private fun BarSlot(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SlotLabelGap, Alignment.CenterVertically),
        modifier = modifier
            .height(Dimens.bottomBar)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(Dimens.icon))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabsSlot(
    count: Int,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(R.plurals.nav_tabs_count_cd, count, count)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SlotLabelGap, Alignment.CenterVertically),
        modifier = modifier
            .height(Dimens.bottomBar)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(TEST_TAG_NAV_TABS),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.tabCount)
                .border(TabCountStroke, tint, RoundedCornerShape(TabCountRadius)),
        ) {
            Text(
                text = count.toString(), // BrowserLimits.MAX_TABS keeps this to two digits
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = TAB_COUNT_FONT_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = tint,
            )
        }
        Text(
            text = stringResource(R.string.nav_tabs),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun NewTabAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ThirtySixTheme.colors
    val shape = ThirtySixTheme.shapes.full
    val description = stringResource(R.string.browser_action_new_tab)
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.fab)
                .fabGlow(shape, colors.glow)
                .clip(shape)
                .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(TEST_TAG_NAV_NEW_TAB),
        ) {
            Icon(
                painter = painterResource(AppIcons.Add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(Dimens.iconLarge),
            )
        }
    }
}
