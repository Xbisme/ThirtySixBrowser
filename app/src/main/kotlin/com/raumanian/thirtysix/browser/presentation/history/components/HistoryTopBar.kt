@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalMaterial3Api::class)

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 014 — top bar for the History screen.
 *
 * Composed of a [TopAppBar] (title · back · clear-all action) stacked above an
 * always-visible search field. Keeping the field outside the app-bar title slot
 * leaves room for the destructive clear-all affordance, which FR-023 requires to
 * be visible whenever at least one entry exists — and hidden when none do.
 */
@Composable
@Suppress("LongParameterList")
fun HistoryTopBar(
    searchQuery: String,
    hasEntries: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchQueryClear: () -> Unit,
    onClearAllClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.history_screen_title)) },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag(TEST_TAG_HISTORY_BACK),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.history_action_back),
                    )
                }
            },
            actions = {
                // Spec 014 FR-023 — hidden entirely when there is nothing to clear.
                if (hasEntries) {
                    IconButton(
                        onClick = onClearAllClick,
                        modifier = Modifier.testTag(TEST_TAG_HISTORY_CLEAR_ALL),
                    ) {
                        Icon(
                            // `material-icons-core` ships no DeleteSweep glyph — the
                            // plain Delete icon is the documented fallback (tasks T087).
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.history_action_clear_all),
                        )
                    }
                }
            },
        )
        HistorySearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSearchQueryClear = onSearchQueryClear,
        )
    }
}

/**
 * Spec 014 FR-011 / FR-016 — live search input. Filtering itself is gated in the
 * ViewModel at [com.raumanian.thirtysix.browser.core.constants.BrowserLimits.SEARCH_MIN_CHARS];
 * this field simply reports every keystroke (no debounce, per Q4).
 */
@Composable
private fun HistorySearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchQueryClear: () -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_HISTORY_SEARCH_FIELD)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = onSearchQueryClear,
                    modifier = Modifier.testTag(TEST_TAG_HISTORY_SEARCH_CLEAR),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(
                            R.string.history_search_clear_content_description,
                        ),
                    )
                }
            }
        },
    )
}

const val TEST_TAG_HISTORY_BACK: String = "history_back"
const val TEST_TAG_HISTORY_CLEAR_ALL: String = "history_clear_all"
const val TEST_TAG_HISTORY_SEARCH_FIELD: String = "history_search_field"
const val TEST_TAG_HISTORY_SEARCH_CLEAR: String = "history_search_clear"
