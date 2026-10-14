@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.presentation.browser.LoadingState
internal const val TEST_TAG_BROWSER_TOP_BAR: String = "browser_top_bar"
internal const val TEST_TAG_BROWSER_STAR_ICON: String = "browser_star_icon"
internal const val TEST_TAG_BROWSER_RELOAD_STOP: String = "browser_reload_stop"

private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"

/**
 * Spec 019 — the browsing top bar (design system SearchBar inside `ts-topbar`): the compact
 * address well, the bookmark toggle and Reload — which becomes Stop while loading (FR-023).
 *
 * It pads itself by the status-bar inset, so the well never sits under the status bar
 * (FR-008; the defect Specs 009–018 shipped). An incognito tab paints the bar plum in both
 * themes (FR-051).
 */
@Composable
@Suppress("LongParameterList")
internal fun BrowserTopBar(
    state: BrowserUiState,
    addressBarCallbacks: AddressBarCallbacks,
    onStarTapped: () -> Unit,
    onReloadOrStop: () -> Unit,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    focusRequester: FocusRequester? = null,
) {
    val colors = ThirtySixTheme.colors
    val background = if (state.isIncognito) colors.surfaceIncognito else MaterialTheme.colorScheme.surface
    val content = if (state.isIncognito) colors.onIncognito else MaterialTheme.colorScheme.onSurface
    val isLoading = state.loadingState is LoadingState.Loading
    CompositionLocalProvider(LocalContentColor provides content) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag(TEST_TAG_BROWSER_TOP_BAR),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(
                    start = Spacing.md,
                    end = Spacing.xs,
                    top = Spacing.sm,
                    bottom = Spacing.sm,
                ),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AddressBar(
                        state = state,
                        callbacks = addressBarCallbacks,
                        isLoading = isLoading,
                        progress = progress,
                        focusRequester = focusRequester,
                    )
                }
                TopBarActions(
                    state = state,
                    isLoading = isLoading,
                    onStarTapped = onStarTapped,
                    onReloadOrStop = onReloadOrStop,
                )
            }
        }
    }
}

/** The bookmark toggle (when the page can be bookmarked) and Reload/Stop (FR-023). */
@Composable
private fun TopBarActions(
    state: BrowserUiState,
    isLoading: Boolean,
    onStarTapped: () -> Unit,
    onReloadOrStop: () -> Unit,
) {
    val content = LocalContentColor.current
    if (canBookmarkCurrentPage(state)) {
        RoundIconButton(
            icon = if (state.isBookmarked) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            contentDescription = stringResource(
                if (state.isBookmarked) {
                    R.string.bookmark_action_star_remove_cd
                } else {
                    R.string.bookmark_action_star_add_cd
                },
            ),
            onClick = onStarTapped,
            style = RoundIconStyle.Bare,
            tint = if (state.isBookmarked) MaterialTheme.colorScheme.primary else content,
            modifier = Modifier.testTag(TEST_TAG_BROWSER_STAR_ICON),
        )
    }
    RoundIconButton(
        icon = if (isLoading) AppIcons.Close else AppIcons.Refresh,
        contentDescription = stringResource(
            if (isLoading) R.string.browser_action_stop else R.string.browser_action_reload,
        ),
        onClick = onReloadOrStop,
        style = RoundIconStyle.Bare,
        modifier = Modifier.testTag(TEST_TAG_BROWSER_RELOAD_STOP),
    )
}

/**
 * Spec 013 FR-004 — the star is hidden (not disabled) when the page cannot be bookmarked:
 * incognito tab, error state, or a non-http(s) address (research R11 of Spec 013).
 */
private fun canBookmarkCurrentPage(state: BrowserUiState): Boolean {
    val url = state.currentUrl
    val isHttp = url.startsWith(HTTP_PREFIX, ignoreCase = true) || url.startsWith(HTTPS_PREFIX, ignoreCase = true)
    return !state.isIncognito && state.loadingState !is LoadingState.Failed && url.isNotBlank() && isHttp
}
