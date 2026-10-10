@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.components.RoundIconButton
import com.raumanian.thirtysix.browser.presentation.components.RoundIconStyle
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

/**
 * Spec 014 / Spec 019 — top of the History screen: a bar with Back and the clear-all icon,
 * the large "History" title, then the always-visible search well (FR-041).
 *
 * It pads itself by the status-bar inset so nothing sits under the status bar (FR-008,
 * Constitution §V). The clear-all affordance is visible whenever at least one entry exists
 * and hidden when none do (Spec 014 FR-023).
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        ) {
            RoundIconButton(
                icon = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.history_action_back),
                onClick = onBackClick,
                style = RoundIconStyle.Bare,
                modifier = Modifier.testTag(TEST_TAG_HISTORY_BACK),
            )
            Spacer(modifier = Modifier.weight(1f))
            // Spec 014 FR-023 — hidden entirely when there is nothing to clear.
            if (hasEntries) {
                RoundIconButton(
                    icon = AppIcons.DeleteSweep,
                    contentDescription = stringResource(R.string.history_action_clear_all),
                    onClick = onClearAllClick,
                    style = RoundIconStyle.Bare,
                    modifier = Modifier.testTag(TEST_TAG_HISTORY_CLEAR_ALL),
                )
            }
        }
        Text(
            text = stringResource(R.string.history_screen_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        HistorySearchWell(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSearchQueryClear = onSearchQueryClear,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
    }
}

/**
 * Spec 014 FR-011 / FR-016 — live search input, drawn as the rounded well the address bar
 * uses (Spec 019). Filtering itself is gated in the ViewModel at
 * [com.raumanian.thirtysix.browser.core.constants.BrowserLimits.SEARCH_MIN_CHARS]; this field
 * simply reports every keystroke (no debounce, per Q4).
 */
@Composable
private fun HistorySearchWell(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchQueryClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = ThirtySixTheme.shapes.full
    BasicTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_HISTORY_SEARCH_FIELD),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .height(Dimens.searchWell)
                    .clip(shape)
                    .background(scheme.surfaceContainer)
                    .padding(start = Spacing.md - Spacing.xs, end = Spacing.xs),
            ) {
                Icon(
                    painter = painterResource(AppIcons.Search),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.history_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (searchQuery.isNotEmpty()) {
                    RoundIconButton(
                        icon = AppIcons.Close,
                        contentDescription = stringResource(R.string.history_search_clear_content_description),
                        onClick = onSearchQueryClear,
                        style = RoundIconStyle.Bare,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TEST_TAG_HISTORY_SEARCH_CLEAR),
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
