@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import java.io.File

/**
 * Spec 011 Q4 amendment (2026-05-03) — Chrome-style 3-zone tab card.
 *
 * Layout (top → bottom):
 *  - 16:9 preview area: WebView screenshot (`screenshotFile`) when cached,
 *    falling back to a deterministic colored placeholder + first-letter
 *    glyph derived from the hostname.
 *  - Title row: 16dp favicon (or hostname-glyph fallback) + page title +
 *    close × `IconButton`. Title falls back to "New tab" when empty (R11).
 *  - Hostname row: URL hostname extracted via [extractHostnameOrSelf].
 *
 * Active-tab visual indicator: `BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`
 * on the [Card] when [isActive] is true.
 *
 * `Modifier.semantics { contentDescription = "${title}, ${hostname}" }`
 * exposes the tab identity to TalkBack as a single read.
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
    val displayTitle = tab.title.ifBlank { stringResource(R.string.tabs_default_title) }
    val hostname = tab.url.extractHostnameOrSelf()
    val a11y = if (tab.isIncognito) {
        // Spec 012 — incognito card semantics MUST NOT leak title/hostname
        // (the tab is "private" — the very thing the user is hiding from a
        // glancing observer). FR-025 dedicated a11y label.
        stringResource(R.string.tabs_a11y_incognito_card_placeholder)
    } else {
        "$displayTitle, $hostname"
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_TAB_CARD_PREFIX + tab.id)
            .semantics { contentDescription = a11y },
        border = if (isActive) {
            BorderStroke(ACTIVE_BORDER_WIDTH_DP, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xs),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabPreviewArea(
                hostname = hostname,
                screenshotFile = screenshotFile,
                isIncognito = tab.isIncognito,
                modifier = Modifier.fillMaxWidth(),
            )
            TabTitleRow(
                title = displayTitle,
                hostname = hostname,
                faviconFile = faviconFile,
                tabId = tab.id,
                isIncognito = tab.isIncognito,
                onCloseClick = onCloseClick,
            )
        }
    }
}

@Composable
private fun TabPreviewArea(
    hostname: String,
    screenshotFile: File?,
    isIncognito: Boolean,
    modifier: Modifier = Modifier,
) {
    // Spec 012 (FR-014, R6) — incognito cards bypass screenshot read entirely
    // and render a distinct surfaceVariant background + lock glyph. The
    // upstream callsite already passes screenshotFile=null for incognito tabs;
    // this branch is defence-in-depth in case of regressions.
    if (isIncognito) {
        Box(
            modifier = modifier
                .aspectRatio(PLACEHOLDER_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(TEST_TAG_INCOGNITO_PLACEHOLDER),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val style = TabPlaceholderColor.forHostname(hostname)
    val (background, foreground) = resolvePalette(style.paletteRoleIndex)
    // Q4 amendment — load screenshot bitmap once per (path, length) so
    // re-rendering for unrelated state (active-tab indicator toggle) does
    // not re-decode. Falls back to colored placeholder when no screenshot.
    val screenshotBitmap = remember(screenshotFile?.absolutePath, screenshotFile?.length()) {
        screenshotFile?.takeIf { it.exists() && it.length() > 0L }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .aspectRatio(PLACEHOLDER_ASPECT_RATIO)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (screenshotBitmap != null) {
            Image(
                bitmap = screenshotBitmap,
                contentDescription = null, // hostname already in card semantics
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = style.glyph.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = foreground,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun TabTitleRow(
    title: String,
    hostname: String,
    faviconFile: File?,
    tabId: Long,
    isIncognito: Boolean,
    onCloseClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        if (isIncognito) {
            IncognitoLeadingGlyph()
        } else {
            FaviconOrPlaceholder(
                faviconFile = faviconFile,
                hostname = hostname,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isIncognito) {
                    stringResource(R.string.tabs_card_incognito_label)
                } else {
                    title
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Spec 012 — incognito tabs do NOT show hostname (privacy).
            // Render a single space to preserve row height parity.
            Text(
                text = if (isIncognito) " " else hostname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.testTag(TEST_TAG_TAB_CARD_CLOSE_PREFIX + tabId),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.tabs_card_close),
            )
        }
    }
}

@Composable
private fun IncognitoLeadingGlyph() {
    Box(
        modifier = Modifier
            .size(FAVICON_DISPLAY_SIZE_DP)
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = stringResource(R.string.tabs_a11y_incognito_glyph),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(INCOGNITO_GLYPH_INNER_SIZE_DP),
        )
    }
}

@Composable
private fun FaviconOrPlaceholder(faviconFile: File?, hostname: String) {
    // Decode favicon once per file path/length combination.
    val faviconBitmap = remember(faviconFile?.absolutePath, faviconFile?.length()) {
        faviconFile?.takeIf { it.exists() && it.length() > 0L }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?.asImageBitmap()
    }
    if (faviconBitmap != null) {
        Image(
            bitmap = faviconBitmap,
            contentDescription = null,
            modifier = Modifier.size(FAVICON_DISPLAY_SIZE_DP),
        )
    } else {
        // Tiny colored circle with the first-letter glyph as a stand-in
        // until the real favicon arrives. Same palette as the preview area
        // for visual coherence within a single card.
        val style = TabPlaceholderColor.forHostname(hostname)
        val (background, foreground) = resolvePalette(style.paletteRoleIndex)
        Box(
            modifier = Modifier
                .size(FAVICON_DISPLAY_SIZE_DP)
                .background(background, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = style.glyph.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
            )
        }
    }
}

@Composable
private fun resolvePalette(paletteRoleIndex: Int): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (paletteRoleIndex) {
        PALETTE_PRIMARY -> cs.primary to cs.onPrimary
        PALETTE_SECONDARY -> cs.secondary to cs.onSecondary
        PALETTE_TERTIARY -> cs.tertiary to cs.onTertiary
        PALETTE_SURFACE_VARIANT -> cs.surfaceVariant to cs.onSurfaceVariant
        PALETTE_PRIMARY_CONTAINER -> cs.primaryContainer to cs.onPrimaryContainer
        PALETTE_SECONDARY_CONTAINER -> cs.secondaryContainer to cs.onSecondaryContainer
        PALETTE_TERTIARY_CONTAINER -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.inversePrimary to cs.inverseOnSurface
    }
}

// File-top constants per Constitution §III No-Hardcode Rule. Test tags are
// per-tab to support `composeTestRule.onNodeWithTag(TEST_TAG_TAB_CARD_PREFIX + id)`.
const val TEST_TAG_TAB_CARD_PREFIX: String = "tabs_card_"
const val TEST_TAG_TAB_CARD_CLOSE_PREFIX: String = "tabs_card_close_"
const val TEST_TAG_INCOGNITO_PLACEHOLDER: String = "tabs_card_incognito_placeholder"

private const val PLACEHOLDER_ASPECT_RATIO: Float = 16f / 9f
private val ACTIVE_BORDER_WIDTH_DP = androidx.compose.ui.unit.Dp(2f)
private val FAVICON_DISPLAY_SIZE_DP = 16.dp
private val INCOGNITO_GLYPH_INNER_SIZE_DP = 12.dp
private const val PALETTE_PRIMARY: Int = 0
private const val PALETTE_SECONDARY: Int = 1
private const val PALETTE_TERTIARY: Int = 2
private const val PALETTE_SURFACE_VARIANT: Int = 3
private const val PALETTE_PRIMARY_CONTAINER: Int = 4
private const val PALETTE_SECONDARY_CONTAINER: Int = 5
private const val PALETTE_TERTIARY_CONTAINER: Int = 6
