@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.presentation.components.QuickTile
import com.raumanian.thirtysix.browser.presentation.components.QuickTileTone
import com.raumanian.thirtysix.browser.presentation.components.RoundIconButton
import com.raumanian.thirtysix.browser.presentation.components.RowBanner
import com.raumanian.thirtysix.browser.presentation.components.SheetFrame
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal const val TEST_TAG_HUB_SHEET: String = "hub_sheet"
internal const val TEST_TAG_HUB_SETTINGS: String = "hub_settings"
internal const val TEST_TAG_HUB_BACK: String = "hub_back"
internal const val TEST_TAG_HUB_FORWARD: String = "hub_forward"
internal const val TEST_TAG_HUB_DOWNLOADS_ACTIVE: String = "hub_downloads_active"

private val TileGap = 10.dp

/**
 * Spec 019 — the hub (design system BottomSheet), opened from Menu; it replaces the overflow
 * menu (FR-030). Settings, Back/Forward, the downloads-in-progress button (only while one
 * runs), the privacy banner, the incognito banner, the three libraries, and four quick
 * settings (FR-031). Any choice closes the sheet first, then acts (FR-032).
 *
 * [hasActiveDownloadsFlow] is collected only here, so the download status is polled only while
 * the hub is open (analysis U2). The sheet's own drag is applied by [SheetFrame] in the layout
 * phase, so dragging does not recompose this content (contracts C6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun HubSheet(
    canGoBack: Boolean,
    canGoForward: Boolean,
    callbacks: HubCallbacks,
    pageTools: HubPageToolsState,
    onDismiss: () -> Unit,
    hasActiveDownloadsFlow: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
    protectionFlow: StateFlow<PageProtectionStatus>? = null,
) {
    val hasActiveDownloads by hasActiveDownloadsFlow.collectAsStateWithLifecycle()
    // Spec 023 — collected only while the hub is open (§V rule 3).
    val protection = protectionFlow?.collectAsStateWithLifecycle()?.value ?: PageProtectionStatus.NOT_A_WEB_PAGE
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeThen: (() -> Unit) -> () -> Unit = { action ->
        {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                onDismiss()
                action()
            }
        }
    }
    SheetFrame(onDismiss = onDismiss, sheetState = sheetState, modifier = modifier.testTag(TEST_TAG_HUB_SHEET)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
        ) {
            HubHeader(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                hasActiveDownloads = hasActiveDownloads,
                callbacks = callbacks,
                closeThen = closeThen,
            )
            HubProtectionSection(
                status = protection,
                onBanner = closeThen(callbacks.onPrivacy),
                onProtectionSwitched = { on -> closeThen { callbacks.onProtectionSwitched(on) }() },
            )
            RowBanner(
                title = stringResource(R.string.hub_incognito_banner_title),
                body = stringResource(R.string.hub_incognito_banner_body),
                coinIcon = AppIcons.IncognitoFilled,
                onClick = closeThen(callbacks.onNewIncognitoTab),
            )
            HubPageTools(state = pageTools, onPageTool = { tool -> closeThen { callbacks.onPageTool(tool) }() })
            HubPlaces(callbacks = callbacks, closeThen = closeThen)
            Text(
                text = stringResource(R.string.hub_quick_settings_title),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            HubQuickSettings(onQuickSetting = { setting -> closeThen { callbacks.onQuickSetting(setting) }() })
        }
    }
}

@Composable
private fun HubHeader(
    canGoBack: Boolean,
    canGoForward: Boolean,
    hasActiveDownloads: Boolean,
    callbacks: HubCallbacks,
    closeThen: (() -> Unit) -> () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RoundIconButton(
            icon = AppIcons.Settings,
            contentDescription = stringResource(R.string.settings_screen_title),
            onClick = closeThen(callbacks.onSettings),
            modifier = Modifier.testTag(TEST_TAG_HUB_SETTINGS),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RoundIconButton(
                icon = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.browser_action_back),
                onClick = closeThen(callbacks.onBack),
                enabled = canGoBack,
                modifier = Modifier.testTag(TEST_TAG_HUB_BACK),
            )
            RoundIconButton(
                icon = AppIcons.ArrowForward,
                contentDescription = stringResource(R.string.browser_action_forward),
                onClick = closeThen(callbacks.onForward),
                enabled = canGoForward,
                modifier = Modifier.testTag(TEST_TAG_HUB_FORWARD),
            )
            if (hasActiveDownloads) {
                RoundIconButton(
                    icon = AppIcons.Downloading,
                    contentDescription = stringResource(R.string.hub_downloads_in_progress_cd),
                    onClick = closeThen(callbacks.onDownloadsInProgress),
                    modifier = Modifier.testTag(TEST_TAG_HUB_DOWNLOADS_ACTIVE),
                )
            }
        }
    }
}

@Composable
private fun HubPlaces(callbacks: HubCallbacks, closeThen: (() -> Unit) -> () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TileGap), modifier = Modifier.fillMaxWidth()) {
        QuickTile(
            label = stringResource(R.string.downloads_screen_title),
            icon = AppIcons.Download,
            tone = QuickTileTone.Place,
            onClick = closeThen(callbacks.onDownloads),
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            label = stringResource(R.string.history_screen_title),
            icon = AppIcons.History,
            tone = QuickTileTone.Place,
            onClick = closeThen(callbacks.onHistory),
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            label = stringResource(R.string.bookmarks_screen_title),
            icon = AppIcons.Bookmarks,
            tone = QuickTileTone.Place,
            onClick = closeThen(callbacks.onBookmarks),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HubQuickSettings(onQuickSetting: (QuickSetting) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TileGap), modifier = Modifier.fillMaxWidth()) {
        QuickTile(
            label = stringResource(R.string.settings_theme_title),
            icon = AppIcons.ContrastFilled,
            tone = QuickTileTone.Sky,
            onClick = { onQuickSetting(QuickSetting.Theme) },
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            label = stringResource(R.string.settings_section_language),
            icon = AppIcons.TranslateFilled,
            tone = QuickTileTone.Sun,
            onClick = { onQuickSetting(QuickSetting.Language) },
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            label = stringResource(R.string.hub_tile_search_engine),
            icon = AppIcons.SearchEngineFilled,
            tone = QuickTileTone.Mint,
            onClick = { onQuickSetting(QuickSetting.SearchEngine) },
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            label = stringResource(R.string.hub_tile_clear_data),
            icon = AppIcons.MopFilled,
            tone = QuickTileTone.Peach,
            onClick = { onQuickSetting(QuickSetting.ClearData) },
            modifier = Modifier.weight(1f),
        )
    }
}
