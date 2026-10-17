@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.LoadingState
internal const val TEST_TAG_BROWSER_TOP_BAR: String = "browser_top_bar"
internal const val TEST_TAG_BROWSER_STAR_ICON: String = "browser_star_icon"
internal const val TEST_TAG_BROWSER_RELOAD_STOP: String = "browser_reload_stop"

private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"

/**
 * Address well, bookmark toggle and Reload/Stop. Pads itself by the status-bar inset so the well
 * never sits under the status bar; incognito paints it plum in both themes.
 */
@Composable
@Suppress("LongParameterList")
internal fun BrowserTopBar(
    state: BrowserUiState,
    addressBarCallbacks: AddressBarCallbacks,
    onStarTap: () -> Unit,
    onReloadOrStop: () -> Unit,
    modifier: Modifier = Modifier,
    addressFieldModifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    focusRequester: FocusRequester? = null,
    addressLane: @Composable (pill: @Composable () -> Unit) -> Unit = { pill -> pill() },
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
                    addressLane {
                        AddressBar(
                            state = state,
                            callbacks = addressBarCallbacks,
                            isLoading = isLoading,
                            progress = progress,
                            focusRequester = focusRequester,
                            fieldModifier = addressFieldModifier,
                        )
                    }
                }
                TopBarActions(
                    state = state,
                    isLoading = isLoading,
                    onStarTap = onStarTap,
                    onReloadOrStop = onReloadOrStop,
                )
            }
        }
    }
}

@Composable
private fun RowScope.TopBarActions(
    state: BrowserUiState,
    isLoading: Boolean,
    onStarTap: () -> Unit,
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
            onClick = onStarTap,
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

/** The star is hidden, not disabled, in incognito, on an error, or for a non-http(s) address. */
private fun canBookmarkCurrentPage(state: BrowserUiState): Boolean {
    val url = state.currentUrl
    val isHttp = url.startsWith(HTTP_PREFIX, ignoreCase = true) || url.startsWith(HTTPS_PREFIX, ignoreCase = true)
    return !state.isIncognito && state.loadingState !is LoadingState.Failed && url.isNotBlank() && isHttp
}
