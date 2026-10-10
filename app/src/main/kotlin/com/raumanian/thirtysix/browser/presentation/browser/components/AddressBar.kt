@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.presentation.components.ProgressLine
import com.raumanian.thirtysix.browser.presentation.components.RoundIconButton
import com.raumanian.thirtysix.browser.presentation.components.RoundIconStyle
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

/** Test tag — `AddressBarTest` and any future instrumented test driving the bar. */
internal const val TEST_TAG_ADDRESS_BAR: String = "address_bar"

/** Test tag — trailing clear button, visible iff focused + non-empty. */
internal const val TEST_TAG_ADDRESS_BAR_CLEAR: String = "address_bar_clear"

/** Test tag — the page-load progress line on the well's bottom edge (FR-024). */
internal const val TEST_TAG_PAGE_LOAD_PROGRESS: String = "page_load_progress"

private const val HTTPS_PREFIX = "https://"
private val FocusRing = 2.dp

/**
 * Spec 009 — the address bar / omnibox; Spec 019 — restyled as the design system's compact
 * search well (SearchBar component, "Browsing" and "Focused" states).
 *
 * Display rules are Spec 009's, unchanged (FR-014–FR-018): unfocused shows only the hostname,
 * focused shows the full URL with everything selected, typing replaces it; IME Go submits;
 * the trailing clear button shows iff focused and non-empty and keeps focus.
 *
 * Leading glyph: the incognito mask in an incognito tab (FR-023), a lock for an `https` page,
 * otherwise the search glyph. Incognito wells are glass on the plum bar (FR-051).
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun AddressBar(
    state: BrowserUiState,
    callbacks: AddressBarCallbacks,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    progress: () -> Float = { 0f },
    focusRequester: FocusRequester? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = state.addressBarText)) }

    LaunchedEffect(state.isAddressBarFocused) {
        if (state.isAddressBarFocused) {
            val url = state.currentUrl
            fieldValue = TextFieldValue(text = url, selection = TextRange(0, url.length))
            callbacks.onTextChange(url)
        } else {
            fieldValue = TextFieldValue(text = "")
            callbacks.onTextChange("")
        }
    }

    LaunchedEffect(state.addressBarText, state.isAddressBarFocused) {
        if (state.isAddressBarFocused && state.addressBarText != fieldValue.text) {
            fieldValue = fieldValue.copy(text = state.addressBarText)
        }
    }

    val displayedValue: TextFieldValue = if (state.isAddressBarFocused) {
        fieldValue
    } else {
        TextFieldValue(text = state.currentUrl.extractHostnameOrSelf())
    }

    val style = addressWellStyle(state)
    val shape = ThirtySixTheme.shapes.full

    Box(modifier = modifier) {
        BasicTextField(
            value = displayedValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                callbacks.onTextChange(newValue.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TEST_TAG_ADDRESS_BAR)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focusState -> callbacks.onFocusChange(focusState.isFocused) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = style.content),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { callbacks.onSubmit() }),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.clip(shape)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier
                            .height(Dimens.searchWell)
                            .clip(shape)
                            .background(style.fill)
                            .border(if (state.isAddressBarFocused) FocusRing else Dimens.hairline, style.ring, shape)
                            .padding(start = Spacing.md - Spacing.xs, end = Spacing.xs),
                    ) {
                        Icon(
                            painter = painterResource(style.leading),
                            contentDescription = null,
                            tint = style.hint,
                            modifier = Modifier.size(Dimens.iconSmall),
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (displayedValue.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.browser_address_bar_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = style.hint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                        if (state.isAddressBarFocused && state.addressBarText.isNotEmpty()) {
                            RoundIconButton(
                                icon = AppIcons.Close,
                                contentDescription = stringResource(R.string.browser_address_bar_clear_cd),
                                onClick = callbacks.onClear,
                                style = RoundIconStyle.Bare,
                                tint = style.content,
                                modifier = Modifier.testTag(TEST_TAG_ADDRESS_BAR_CLEAR),
                            )
                        }
                    }
                    // Spec 019 FR-024 — page-load progress on the well's bottom edge. [progress] is
                    // read only inside ProgressLine's draw phase (contracts C6).
                }
            },
        )
        // Spec 019 FR-024 — page-load progress on the well's bottom edge, kept outside the text
        // field so its label and tag are not merged into the field's semantics. Inset by the pill's
        // corner radius so it runs along the flat part of the edge. [progress] is read only inside
        // ProgressLine's draw phase (contracts C6).
        if (isLoading) {
            val loadingDescription = stringResource(R.string.browser_loading_a11y)
            ProgressLine(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Dimens.searchWell / 2)
                    .semantics { contentDescription = loadingDescription }
                    .testTag(TEST_TAG_PAGE_LOAD_PROGRESS),
            )
        }
    }
}

private const val HINT_ALPHA = 0.7f

/** Colours and glyph of the well for the current tab and focus state (FR-023, FR-051). */
private data class AddressWellStyle(
    val content: Color,
    val hint: Color,
    @param:DrawableRes val leading: Int,
    val fill: Color,
    val ring: Color,
)

@Composable
private fun addressWellStyle(state: BrowserUiState): AddressWellStyle {
    val scheme = MaterialTheme.colorScheme
    val colors = ThirtySixTheme.colors
    val incognito = state.isIncognito
    val leading = when {
        incognito -> AppIcons.Incognito
        !state.isAddressBarFocused && state.currentUrl.startsWith(HTTPS_PREFIX, ignoreCase = true) -> AppIcons.Lock
        else -> AppIcons.Search
    }
    val ring = when {
        state.isAddressBarFocused -> scheme.primary
        incognito -> colors.glassStroke
        else -> Color.Transparent
    }
    return AddressWellStyle(
        content = if (incognito) colors.onIncognito else scheme.onSurface,
        hint = if (incognito) colors.onIncognito.copy(alpha = HINT_ALPHA) else scheme.onSurfaceVariant,
        leading = leading,
        fill = if (incognito) colors.glass else scheme.surfaceContainer,
        ring = ring,
    )
}
