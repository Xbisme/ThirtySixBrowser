@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.domain.util.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.MeshArt
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SeededMeshArt
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.rememberFileImage
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.presentation.home.seedColorFor
import java.io.File
import kotlin.math.abs
import kotlin.math.sign

private const val SCRIM_ALPHA = 0.55f

/**
 * Spec 019 FR-040 — an image tab card (design system TabCard): the page preview fills the card,
 * the title and favicon sit over a dark fade at the bottom, and a glass close button sits top
 * right. The active tab gets a 2dp `primary` ring with a 2dp gap.
 *
 * - No screenshot yet → a mesh seeded from the host with its first letter.
 * - A tab on the Home page → the hero mesh, "New tab" and the brand mark (data-model §3).
 * - An incognito tab → the plum incognito surface, never a screenshot or its title (Spec 012).
 *
 * Swipe sideways to close (clarification Q1): the offset is a float state read **only in
 * `graphicsLayer { }`**, so dragging a card never recomposes it or the grid (research R11, §V
 * rule 1). Past [BrowserLimits.TAB_SWIPE_CLOSE_FRACTION] of the card's width it flies out and
 * [onCloseClick] fires once; short of it, it springs back.
 */
@Suppress("LongParameterList")
@Composable
fun TabSwitcherCard(
    tab: Tab,
    isActive: Boolean,
    faviconFile: File?,
    screenshotFile: File?,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHome = tab.url == UrlConstants.HOME_PAGE_URL
    val displayTitle = if (isHome) {
        stringResource(R.string.tabs_default_title)
    } else {
        tab.title.ifBlank { stringResource(R.string.tabs_default_title) }
    }
    val hostname = if (isHome) "" else tab.url.extractHostnameOrSelf()
    val a11y = if (tab.isIncognito) {
        // Spec 012 FR-025 — an incognito card never speaks its title or host.
        stringResource(R.string.tabs_a11y_incognito_card_placeholder)
    } else {
        listOf(displayTitle, hostname).filter { it.isNotEmpty() }.joinToString(", ")
    }
    val shape = MaterialTheme.shapes.extraLarge
    SwipeToClose(
        onClose = onCloseClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.tabCardHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isActive) {
                        Modifier
                            .border(Dimens.activeRing, MaterialTheme.colorScheme.primary, shape)
                            .padding(Dimens.activeRing * 2)
                    } else {
                        Modifier
                    },
                ),
        ) {
            CardFace(
                tab = tab,
                isHome = isHome,
                title = if (tab.isIncognito) stringResource(R.string.tabs_card_incognito_label) else displayTitle,
                hostname = hostname,
                a11y = a11y,
                faviconFile = faviconFile,
                screenshotFile = screenshotFile,
                onClick = onClick,
            )
            GlassClose(
                onClick = onCloseClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .testTag(TEST_TAG_TAB_CARD_CLOSE_PREFIX + tab.id),
            )
        }
    }
}

/**
 * Horizontal swipe-to-close. The offset is written by the gesture and read only in
 * `graphicsLayer { }` (draw phase), so a drag never recomposes the card (research R11).
 */
@Composable
private fun SwipeToClose(onClose: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer {
                translationX = offset
                alpha = if (widthPx == 0) 1f else 1f - (abs(offset) / widthPx).coerceIn(0f, 1f)
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> offset += delta },
                onDragStopped = {
                    val closes = widthPx > 0 && abs(offset) > widthPx * BrowserLimits.TAB_SWIPE_CLOSE_FRACTION
                    val target = if (closes) widthPx * sign(offset) else 0f
                    animate(initialValue = offset, targetValue = target) { value, _ -> offset = value }
                    if (closes) onClose()
                },
            ),
    ) {
        content()
    }
}

@Suppress("LongParameterList")
@Composable
private fun CardFace(
    tab: Tab,
    isHome: Boolean,
    title: String,
    hostname: String,
    a11y: String,
    faviconFile: File?,
    screenshotFile: File?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(TEST_TAG_TAB_CARD_PREFIX + tab.id)
            .semantics { contentDescription = a11y },
    ) {
        when {
            tab.isIncognito -> IncognitoPreview()
            isHome -> HomePreview()
            else -> PagePreview(hostname = hostname, screenshotFile = screenshotFile)
        }
        CardCaption(
            title = title,
            lead = {
                when {
                    tab.isIncognito -> Unit
                    isHome -> Image(
                        painter = painterResource(AppIcons.BrandMark),
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.favicon),
                    )
                    else -> Favicon(faviconFile)
                }
            },
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun PagePreview(hostname: String, screenshotFile: File?) {
    val screenshot by rememberFileImage(screenshotFile)
    val image = screenshot
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        SeededMeshArt(
            seed = seedColorFor(hostname),
            letter = hostname.firstOrNull()?.uppercase(),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HomePreview() {
    val colors = ThirtySixTheme.colors
    MeshArt(
        base = colors.heroDeep,
        glowA = colors.heroViolet,
        glowB = colors.heroRose,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun IncognitoPreview() {
    val colors = ThirtySixTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceIncognito)
            .testTag(TEST_TAG_INCOGNITO_PLACEHOLDER),
    ) {
        Icon(
            painter = painterResource(AppIcons.IncognitoFilled),
            contentDescription = stringResource(R.string.tabs_a11y_incognito_glyph),
            tint = colors.onIncognito,
            modifier = Modifier.size(Dimens.iconLarge),
        )
    }
}

@Composable
private fun CardCaption(title: String, lead: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = ThirtySixTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.heroDeep.copy(alpha = SCRIM_ALPHA))))
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.lg, bottom = Spacing.sm),
    ) {
        lead()
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onGlass,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Favicon(faviconFile: File?) {
    val favicon by rememberFileImage(faviconFile)
    val image = favicon ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = Modifier.size(Dimens.favicon).clip(MaterialTheme.shapes.extraSmall),
    )
}

/** The design system's small glass close button (28dp visual, 48dp touch target). */
@Composable
private fun GlassClose(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ThirtySixTheme.colors
    val shape = ThirtySixTheme.shapes.full
    val label = stringResource(R.string.tabs_card_close)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.tabCardClose)
                .background(colors.glass, shape)
                .border(Dimens.hairline, colors.glassStroke, shape),
        ) {
            Icon(
                painter = painterResource(AppIcons.Close),
                contentDescription = null,
                tint = colors.onGlass,
                modifier = Modifier.size(Dimens.iconSmall - Spacing.xs),
            )
        }
    }
}

// Test tags are per-tab so tests can find `TEST_TAG_TAB_CARD_PREFIX + id`.
const val TEST_TAG_TAB_CARD_PREFIX: String = "tabs_card_"
const val TEST_TAG_TAB_CARD_CLOSE_PREFIX: String = "tabs_card_close_"
const val TEST_TAG_INCOGNITO_PLACEHOLDER: String = "tabs_card_incognito_placeholder"
