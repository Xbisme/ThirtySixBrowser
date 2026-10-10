@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.incognito

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.theme.fabGlow

internal const val TEST_TAG_INCOGNITO_PAGE: String = "incognito_page"

private val PanelPadding = 18.dp
private val PointGap = 14.dp
private val PointIconGap = 12.dp
private const val FOOTNOTE_ALPHA = 0.75f

/**
 * Spec 019 FR-050 — what an empty incognito tab shows instead of the Home page (FR-015): what is
 * and is not kept, on the plum incognito surface in both themes (FR-051). Every statement here
 * describes what Spec 012 actually does; nothing personal from normal tabs is shown.
 */
@Composable
fun IncognitoPage(bottomPadding: Dp, modifier: Modifier = Modifier) {
    val colors = ThirtySixTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceIncognito)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = bottomPadding + Spacing.lg)
            .testTag(TEST_TAG_INCOGNITO_PAGE),
    ) {
        IncognitoCoin()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(R.string.incognito_page_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onIncognito,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.incognito_page_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onIncognito,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(PointGap),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.glass, MaterialTheme.shapes.large)
                .border(Dimens.hairline, colors.glassStroke, MaterialTheme.shapes.large)
                .padding(PanelPadding),
        ) {
            Text(
                text = stringResource(R.string.incognito_page_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onIncognito.copy(alpha = FOOTNOTE_ALPHA),
            )
            Point(AppIcons.HistoryOff, R.string.incognito_page_point_history)
            Point(AppIcons.CookieOff, R.string.incognito_page_point_cookies)
            Point(AppIcons.ScreenshotBlocked, R.string.incognito_page_point_screenshots)
        }
        Text(
            text = stringResource(R.string.incognito_page_footnote),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onIncognito.copy(alpha = FOOTNOTE_ALPHA),
        )
    }
}

@Composable
private fun IncognitoCoin() {
    val colors = ThirtySixTheme.colors
    val shape = ThirtySixTheme.shapes.full
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Dimens.incognitoCoin)
            .fabGlow(shape, colors.glow)
            .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)), shape),
    ) {
        Icon(
            painter = painterResource(AppIcons.IncognitoFilled),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Dimens.iconLarge),
        )
    }
}

@Composable
private fun Point(@DrawableRes icon: Int, @StringRes text: Int) {
    val colors = ThirtySixTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(PointIconGap), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, tint = colors.onIncognito, modifier = Modifier.size(Dimens.iconSmall))
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium, color = colors.onIncognito)
    }
}
