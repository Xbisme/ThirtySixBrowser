@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.suggestions

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
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.domain.model.SuggestionSource
import com.raumanian.thirtysix.browser.presentation.components.ListRow
import com.raumanian.thirtysix.browser.presentation.components.ListRowIcon
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import kotlinx.coroutines.flow.StateFlow

internal const val TEST_TAG_ADDRESS_SUGGESTIONS: String = "address_suggestions"
internal const val TEST_TAG_ADDRESS_SUGGESTION_ROW: String = "address_suggestion_row"

/**
 * Spec 022 US2 — the suggestions under an address entry (FR-014, FR-018).
 *
 * It collects [stateFlow] itself, so each keystroke recomposes this list and nothing around it
 * (Constitution §V rule 3). It is a plain [Column] keyed by address rather than a lazy list: there
 * are at most six rows, and on the Home page it sits inside the page's own `LazyColumn`, where a
 * second lazy list in the same direction is not allowed (§V rule 6). Each row reads as "Bookmark:"
 * or "History:" plus title and address (FR-036). With no suggestion, nothing is drawn (FR-021).
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

/**
 * Spec 022 research R6 — on a web page the list floats over the page just under the top bar. It
 * scrolls within the space above the keyboard (§V rule 7), so a row is never hidden behind it.
 */
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
