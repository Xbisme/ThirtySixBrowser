@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.model.HomeShortcut
import com.raumanian.thirtysix.browser.domain.model.ShortcutKind
import com.raumanian.thirtysix.browser.presentation.components.ListRow
import com.raumanian.thirtysix.browser.presentation.components.ListRowLetter
import com.raumanian.thirtysix.browser.presentation.components.PillButton
import com.raumanian.thirtysix.browser.presentation.components.PillStyle
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

internal const val TEST_TAG_HOME_PAGE: String = "home_page"
internal const val TEST_TAG_HOME_RECENT_TITLE: String = "home_recent_title"

private const val KEY_HERO = "hero"
private const val KEY_RECENT_HEADER = "recent_header"
private const val TYPE_HERO = "hero"
private const val TYPE_HEADER = "header"
private const val TYPE_RECENT = "recent"

/** Spec 019 contracts C4 — what tapping things on the Home page does. */
data class HomeCallbacks(
    val onSearchSubmit: (String) -> Unit,
    /** A Saved or Recent shortcut, or a recently visited row: load this address in the tab. */
    val onOpenUrl: (String) -> Unit,
    val onSeeAllHistory: () -> Unit,
    val onNewIncognitoTab: () -> Unit,
)

/**
 * Spec 019 US2 — the Home page shown inside a tab (FR-010): the hero, then "Recently visited"
 * with "See all". One `LazyColumn`; every item has a stable key and a content type
 * (Constitution v1.4.0 §V rule 6). With no history the section is absent (FR-014), and with no
 * shortcuts the card row is absent — no empty boxes on a fresh install.
 */
@Composable
fun HomePage(
    callbacks: HomeCallbacks,
    searchFocus: FocusRequester,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    HomeContent(
        state = state,
        callbacks = callbacks,
        onShortcut = { shortcut ->
            val tabId = shortcut.tabId
            if (shortcut.kind == ShortcutKind.Tab && tabId != null) {
                viewModel.onTabShortcut(tabId)
            } else {
                callbacks.onOpenUrl(shortcut.url)
            }
        },
        searchFocus = searchFocus,
        bottomPadding = bottomPadding,
        modifier = modifier,
    )
}

/** Stateless body of [HomePage], for tests. */
@Composable
@Suppress("LongParameterList")
fun HomeContent(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    onShortcut: (HomeShortcut) -> Unit,
    searchFocus: FocusRequester,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(TEST_TAG_HOME_PAGE),
    ) {
        item(key = KEY_HERO, contentType = TYPE_HERO) {
            HomeHero(
                state = state,
                listState = listState,
                onSearchSubmit = callbacks.onSearchSubmit,
                onShortcut = onShortcut,
                onNewIncognitoTab = callbacks.onNewIncognitoTab,
                searchFocus = searchFocus,
            )
        }
        if (state.recent.isNotEmpty()) {
            item(key = KEY_RECENT_HEADER, contentType = TYPE_HEADER) {
                RecentHeader(onSeeAll = callbacks.onSeeAllHistory)
            }
            items(items = state.recent, key = { it.id }, contentType = { TYPE_RECENT }) { entry ->
                RecentRow(entry = entry, onClick = { callbacks.onOpenUrl(entry.url) })
            }
        }
    }
}

@Composable
private fun RecentHeader(onSeeAll: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.home_recent_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }.testTag(TEST_TAG_HOME_RECENT_TITLE),
        )
        PillButton(label = stringResource(R.string.home_recent_see_all), onClick = onSeeAll, style = PillStyle.Text)
    }
}

@Composable
private fun RecentRow(entry: HistoryEntry, onClick: () -> Unit) {
    val host = entry.url.extractHostnameOrSelf()
    ListRow(
        title = entry.title.ifBlank { host },
        subtitle = host,
        leading = { ListRowLetter(letter = host.take(1).uppercase()) },
        onClick = onClick,
    )
}
