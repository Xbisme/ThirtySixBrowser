@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

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
import com.raumanian.thirtysix.browser.core.domain.util.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ProgressLine
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.LoadingState
internal const val TEST_TAG_ADDRESS_BAR: String = "address_bar"

internal const val TEST_TAG_ADDRESS_BAR_CLEAR: String = "address_bar_clear"

internal const val TEST_TAG_PAGE_LOAD_PROGRESS: String = "page_load_progress"

private const val HTTPS_PREFIX = "https://"
private val FocusRing = 2.dp

/**
 * Unfocused shows only the hostname; focused shows the full URL, all selected, so typing replaces
 * it. The leading glyph is the incognito mask, a lock for `https`, or search.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun AddressBar(
    state: BrowserUiState,
    callbacks: AddressBarCallbacks,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    isLoading: Boolean = false,
    progress: () -> Float = { 0f },
    focusRequester: FocusRequester? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = state.addressBarText)) }
    // Texts reported but not yet echoed back through the view model. A busy main thread can deliver
    // an earlier keystroke's echo after later ones; taking it as an outside change dropped keystrokes.
    val pendingEchoes = remember { ArrayDeque<String>() }
    val report: (String) -> Unit = { text ->
        pendingEchoes.addLast(text)
        callbacks.onTextChange(text)
    }

    LaunchedEffect(state.isAddressBarFocused) {
        if (state.isAddressBarFocused) {
            // Runs a frame after focus: if typing already started, selecting all now would let the
            // next keystroke replace what was typed.
            if (fieldValue.text.isEmpty()) {
                val url = state.currentUrl
                fieldValue = TextFieldValue(text = url, selection = TextRange(0, url.length))
                report(url)
            }
        } else {
            fieldValue = TextFieldValue(text = "")
            report("")
        }
    }

    LaunchedEffect(state.addressBarText, state.isAddressBarFocused) {
        val incoming = state.addressBarText
        val echo = pendingEchoes.indexOf(incoming)
        when {
            // Our own earlier text coming back: forget it and everything reported before it.
            echo >= 0 -> repeat(echo + 1) { pendingEchoes.removeFirst() }
            // A change from outside the field (Clear, an incoming link): show it.
            state.isAddressBarFocused && incoming != fieldValue.text -> {
                pendingEchoes.clear()
                fieldValue = TextFieldValue(text = incoming, selection = TextRange(incoming.length))
            }
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
                val changed = newValue.text != fieldValue.text
                fieldValue = newValue
                if (changed) report(newValue.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TEST_TAG_ADDRESS_BAR)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focusState -> callbacks.onFocusChange(focusState.isFocused) }
                .then(fieldModifier),
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
                }
            },
        )
        // Outside the text field so its label and tag are not merged into the field's semantics;
        // inset by the pill's radius to run along the flat edge. [progress] is read only when drawing.
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

private data class AddressWellStyle(
    val content: Color,
    val hint: Color,
    @param:DrawableRes val leading: Int,
    val fill: Color,
    val ring: Color,
)

@DrawableRes
internal fun addressLeadingIcon(incognito: Boolean, focused: Boolean, url: String, loading: LoadingState): Int = when {
    incognito -> AppIcons.Incognito
    !focused && loading !is LoadingState.Failed && url.startsWith(HTTPS_PREFIX, ignoreCase = true) -> AppIcons.Lock
    else -> AppIcons.Search
}

@Composable
private fun addressWellStyle(state: BrowserUiState): AddressWellStyle {
    val scheme = MaterialTheme.colorScheme
    val colors = ThirtySixTheme.colors
    val incognito = state.isIncognito
    val leading = addressLeadingIcon(incognito, state.isAddressBarFocused, state.currentUrl, state.loadingState)
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
