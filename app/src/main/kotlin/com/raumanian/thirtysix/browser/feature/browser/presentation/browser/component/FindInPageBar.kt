@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.FindInPageState
import kotlinx.coroutines.flow.StateFlow

internal const val TEST_TAG_FIND_BAR: String = "find_bar"
internal const val TEST_TAG_FIND_FIELD: String = "find_field"
internal const val TEST_TAG_FIND_COUNT: String = "find_count"
internal const val TEST_TAG_FIND_PREVIOUS: String = "find_previous"
internal const val TEST_TAG_FIND_NEXT: String = "find_next"
internal const val TEST_TAG_FIND_CLOSE: String = "find_close"

/** Spec 022 — what the find bar reports; the screen forwards each to the ViewModel and the WebView. */
internal data class FindInPageCallbacks(
    val onQueryChange: (String) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * Spec 022 US1 — the find bar, which takes the top bar's place while open (FR-005).
 *
 * It collects [stateFlow] itself, so a result per keystroke recomposes this bar and nothing
 * around it (Constitution §V rule 3). The field takes focus with the keyboard up (FR-006). The
 * count is a polite live region, so a screen reader hears it change (FR-036). Previous and Next
 * are disabled when nothing matches (FR-008). Like the top bar it replaces, it pads itself below
 * the status bar (§V rule 7).
 */
@Composable
internal fun FindInPageBar(
    stateFlow: StateFlow<FindInPageState>,
    isIncognito: Boolean,
    callbacks: FindInPageCallbacks,
    modifier: Modifier = Modifier,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val colors = ThirtySixTheme.colors
    val background = if (isIncognito) colors.surfaceIncognito else MaterialTheme.colorScheme.surface
    val content = if (isIncognito) colors.onIncognito else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = modifier
                .fillMaxWidth()
                .background(background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
                .testTag(TEST_TAG_FIND_BAR),
        ) {
            FindField(state = state, onQueryChange = callbacks.onQueryChange, modifier = Modifier.weight(1f))
            RoundIconButton(
                icon = AppIcons.ArrowUp,
                contentDescription = stringResource(R.string.find_in_page_previous),
                onClick = callbacks.onPrevious,
                style = RoundIconStyle.Bare,
                enabled = state.canStep,
                modifier = Modifier.testTag(TEST_TAG_FIND_PREVIOUS),
            )
            RoundIconButton(
                icon = AppIcons.ArrowDown,
                contentDescription = stringResource(R.string.find_in_page_next),
                onClick = callbacks.onNext,
                style = RoundIconStyle.Bare,
                enabled = state.canStep,
                modifier = Modifier.testTag(TEST_TAG_FIND_NEXT),
            )
            RoundIconButton(
                icon = AppIcons.Close,
                contentDescription = stringResource(R.string.find_in_page_close),
                onClick = callbacks.onClose,
                style = RoundIconStyle.Bare,
                modifier = Modifier.testTag(TEST_TAG_FIND_CLOSE),
            )
        }
    }
}

/** The search well: icon, text field, and the "current/total" count. */
@Composable
private fun FindField(state: FindInPageState, onQueryChange: (String) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = ThirtySixTheme.shapes.full
    val content = LocalContentColor.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = state.query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = content),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier
            .focusRequester(focusRequester)
            .testTag(TEST_TAG_FIND_FIELD),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .height(Dimens.searchWell)
                    .clip(shape)
                    .background(scheme.surfaceContainerHigh)
                    .border(Dimens.hairline, scheme.outlineVariant, shape)
                    .padding(start = Spacing.md - Spacing.xs, end = Spacing.md - Spacing.xs),
            ) {
                Icon(
                    painter = painterResource(AppIcons.FindInPage),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.find_in_page_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (state.counterVisible) FindCount(state)
            }
        },
    )
}

@Composable
private fun FindCount(state: FindInPageState) {
    val description = if (state.canStep) {
        stringResource(R.string.find_in_page_count_cd, state.current, state.total)
    } else {
        stringResource(R.string.find_in_page_no_matches_cd)
    }
    Text(
        text = stringResource(R.string.find_in_page_count, state.current, state.total),
        style = MaterialTheme.typography.labelLarge,
        color = if (state.canStep) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        maxLines = 1,
        modifier = Modifier
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
            .testTag(TEST_TAG_FIND_COUNT),
    )
}
