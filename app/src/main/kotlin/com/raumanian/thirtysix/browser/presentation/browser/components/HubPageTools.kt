@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.browser.PageTool
import com.raumanian.thirtysix.browser.presentation.components.QuickTile
import com.raumanian.thirtysix.browser.presentation.components.QuickTileState
import com.raumanian.thirtysix.browser.presentation.components.QuickTileTone
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

internal const val TEST_TAG_HUB_TOOL_FIND: String = "hub_tool_find"
internal const val TEST_TAG_HUB_TOOL_SHARE: String = "hub_tool_share"
internal const val TEST_TAG_HUB_TOOL_PRINT: String = "hub_tool_print"
internal const val TEST_TAG_HUB_TOOL_DESKTOP: String = "hub_tool_desktop"
internal const val TEST_TAG_HUB_TOOL_TEXT_SIZE: String = "hub_tool_text_size"

private val TileGap = 10.dp

/** Tiles per row: three fit at 360dp in German, five do not (research R9). */
private const val TILES_PER_ROW = 3

/**
 * Spec 022 — what the hub's page-tools section needs to know about the page on screen.
 *
 * [available] comes from `PageToolsAvailability`; [isDesktopMode] is the active tab's mode
 * (FR-003); [textSizePercent] is the app-wide text size the Text size tile shows.
 */
@Immutable
internal data class HubPageToolsState(
    val available: Set<PageTool>,
    val isDesktopMode: Boolean,
    val textSizePercent: Int,
)

/**
 * Spec 022 research R9 — the hub's page tools, in two rows: Find · Share · Print, then
 * Desktop site · Text size. A tool that is not available for this page is left out rather than
 * shown disabled (FR-002), so on Home the section is the Text size tile alone. Rows keep three
 * slots, so a short row's tiles stay the same width as a full row's.
 */
@Composable
internal fun HubPageTools(state: HubPageToolsState, onPageTool: (PageTool) -> Unit) {
    val rows = listOf(
        listOf(PageTool.Find, PageTool.Share, PageTool.Print),
        listOf(PageTool.DesktopSite, PageTool.TextSize),
    ).map { row -> row.filter { it in state.available } }.filter { it.isNotEmpty() }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.hub_page_tools_title),
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tool -> PageToolTile(tool, state, onPageTool, Modifier.weight(1f)) }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PageToolTile(tool: PageTool, state: HubPageToolsState, onPageTool: (PageTool) -> Unit, modifier: Modifier) {
    val onClick = { onPageTool(tool) }
    when (tool) {
        PageTool.Find -> QuickTile(
            label = stringResource(R.string.hub_tile_find_in_page),
            icon = AppIcons.FindInPage,
            tone = QuickTileTone.Place,
            onClick = onClick,
            modifier = modifier.testTag(TEST_TAG_HUB_TOOL_FIND),
        )
        PageTool.Share -> QuickTile(
            label = stringResource(R.string.hub_tile_share),
            icon = AppIcons.Share,
            tone = QuickTileTone.Place,
            onClick = onClick,
            modifier = modifier.testTag(TEST_TAG_HUB_TOOL_SHARE),
        )
        PageTool.Print -> QuickTile(
            label = stringResource(R.string.hub_tile_print),
            icon = AppIcons.Print,
            tone = QuickTileTone.Place,
            onClick = onClick,
            modifier = modifier.testTag(TEST_TAG_HUB_TOOL_PRINT),
        )
        PageTool.DesktopSite -> QuickTile(
            label = stringResource(R.string.hub_tile_desktop_site),
            icon = AppIcons.DesktopSite,
            tone = QuickTileTone.Place,
            onClick = onClick,
            state = QuickTileState(selected = state.isDesktopMode),
            modifier = modifier.testTag(TEST_TAG_HUB_TOOL_DESKTOP),
        )
        PageTool.TextSize -> QuickTile(
            label = stringResource(R.string.hub_tile_text_size),
            icon = AppIcons.TextSize,
            tone = QuickTileTone.Place,
            onClick = onClick,
            state = QuickTileState(value = stringResource(R.string.text_size_level, state.textSizePercent)),
            modifier = modifier.testTag(TEST_TAG_HUB_TOOL_TEXT_SIZE),
        )
    }
}
