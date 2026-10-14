@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.feature.browser.domain.model.SuggestionSource
import kotlinx.coroutines.flow.StateFlow

internal const val TEST_TAG_ADDRESS_SUGGESTIONS: String = "address_suggestions"
internal const val TEST_TAG_ADDRESS_SUGGESTION_ROW: String = "address_suggestion_row"

/**
 * Collects [stateFlow] itself, so a keystroke recomposes only this list. A plain [Column], not a
 * lazy list: at most six rows, and on Home it sits inside the page's own `LazyColumn`.
 */
@Composable
internal fun AddressSuggestionList(
    stateFlow: StateFlow<AddressSuggestionsUiState>,
    onPick: (AddressSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    if (state.suggestions.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = Spacing.xs)
            .testTag(TEST_TAG_ADDRESS_SUGGESTIONS),
    ) {
        state.suggestions.forEach { suggestion ->
            key(suggestion.url) { SuggestionRow(suggestion = suggestion, onPick = onPick) }
        }
    }
}

/** On a web page the list floats under the top bar and scrolls within the space above the keyboard. */
@Composable
internal fun BrowserAddressSuggestions(
    visible: Boolean,
    topBarHeight: Dp,
    stateFlow: StateFlow<AddressSuggestionsUiState>,
    onPick: (AddressSuggestion) -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topBarHeight)
            .imePadding(),
    ) {
        AddressSuggestionList(
            stateFlow = stateFlow,
            onPick = onPick,
            modifier = Modifier
                .padding(horizontal = Spacing.sm)
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun SuggestionRow(suggestion: AddressSuggestion, onPick: (AddressSuggestion) -> Unit) {
    val title = suggestion.title.ifBlank { suggestion.url.extractHostnameOrSelf() }
    val isBookmark = suggestion.source == SuggestionSource.Bookmark
    val description = stringResource(
        if (isBookmark) R.string.suggestion_bookmark_cd else R.string.suggestion_history_cd,
        title,
        suggestion.url,
    )
    ListRow(
        title = title,
        subtitle = suggestion.url,
        leading = {
            ListRowIcon(if (isBookmark) AppIcons.Bookmark else AppIcons.History)
        },
        onClick = { onPick(suggestion) },
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(TEST_TAG_ADDRESS_SUGGESTION_ROW),
    )
}
