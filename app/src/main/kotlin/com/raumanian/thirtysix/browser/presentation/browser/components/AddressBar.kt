@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState

/** Test tag — `AddressBarTest` and any future instrumented test driving the bar. */
internal const val TEST_TAG_ADDRESS_BAR: String = "address_bar"

/** Test tag — trailing clear button, visible iff focused + non-empty. */
internal const val TEST_TAG_ADDRESS_BAR_CLEAR: String = "address_bar_clear"

/**
 * Spec 009 — top-of-screen address bar / omnibox.
 *
 * Display rules (FR-014/15/16/17/18):
 *  - **Unfocused** + URL has a host → show only the hostname (compact).
 *  - **Unfocused** + URL is `about:blank` / `file://` → show the full URL as
 *    a fallback (`extractHostnameOrSelf`).
 *  - **Unfocused** + no URL loaded yet → empty `value`; placeholder hint
 *    renders.
 *  - **Focused** → show full URL with all text selected (FR-018), driven by
 *    `LaunchedEffect(isAddressBarFocused)` populating from `currentUrl`.
 *  - **Focused** + user typing → user's in-progress text replaces the URL.
 *
 * Submit (FR-011 / FR-013a / Q1+Q2 clarifications):
 *  - IME Go fires [callbacks.onSubmit] which the host wires to the
 *    ViewModel + URL-load chain. Empty input is a no-op (the host's lambda
 *    knows to skip dismiss/loadUrl in that case).
 *
 * Clear (FR-020/21/23/24 — Phase 6 US4):
 *  - Trailing clear icon visible iff focused + non-empty. Tap fires
 *    [callbacks.onClear] which empties the bound text and preserves focus.
 */
@Composable
internal fun AddressBar(
    state: BrowserUiState,
    callbacks: AddressBarCallbacks,
    modifier: Modifier = Modifier,
) {
    // Local TextFieldValue gives us cursor / selection control for the
    // focus-acquired "select all" behavior. The State value is the source
    // of truth for ViewModel mirroring while focused.
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = state.addressBarText)) }

    // On focus acquisition (false → true) populate from currentUrl + select all.
    // On focus release (true → false) clear local text so the next render falls
    // back to hostname-only display via state.currentUrl.
    LaunchedEffect(state.isAddressBarFocused) {
        if (state.isAddressBarFocused) {
            val url = state.currentUrl
            fieldValue = TextFieldValue(
                text = url,
                selection = TextRange(0, url.length),
            )
            callbacks.onTextChange(url)
        } else {
            fieldValue = TextFieldValue(text = "")
            callbacks.onTextChange("")
        }
    }

    // Keep the local TextFieldValue in sync if the ViewModel's text changes
    // for reasons other than focus acquisition (e.g., onAddressBarClear from
    // the trailing icon).
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

    OutlinedTextField(
        value = displayedValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            callbacks.onTextChange(newValue.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_ADDRESS_BAR)
            .onFocusChanged { focusState -> callbacks.onFocusChange(focusState.isFocused) },
        singleLine = true,
        placeholder = { Text(text = stringResource(R.string.browser_address_bar_hint)) },
        trailingIcon = {
            if (state.isAddressBarFocused && state.addressBarText.isNotEmpty()) {
                IconButton(
                    onClick = callbacks.onClear,
                    modifier = Modifier.testTag(TEST_TAG_ADDRESS_BAR_CLEAR),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.browser_address_bar_clear_cd),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { callbacks.onSubmit() }),
    )
}
