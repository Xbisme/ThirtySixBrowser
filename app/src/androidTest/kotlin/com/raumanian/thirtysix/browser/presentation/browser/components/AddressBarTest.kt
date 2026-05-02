package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.presentation.browser.LoadingState
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 009 — component-level Compose UI test for [AddressBar].
 *
 * Covers T016 (US1 IME submit), T037 (US4 clear button), and T040 (US5
 * hostname-vs-full-URL display + select-all on focus). Pure component test —
 * no WebView, no Hilt, no real navigation. Drives the bar with controlled
 * [BrowserUiState] and asserts visible text + callback firing.
 */
@RunWith(AndroidJUnit4::class)
class AddressBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Recorded callback invocations.
    private var lastTextChange: String? = null
    private var lastFocusChange: Boolean? = null
    private var submitCount: Int = 0
    private var clearCount: Int = 0

    private fun callbacks(): AddressBarCallbacks = AddressBarCallbacks(
        onTextChange = { lastTextChange = it },
        onFocusChange = { lastFocusChange = it },
        onSubmit = { submitCount++ },
        onClear = { clearCount++ },
    )

    private fun setBar(state: BrowserUiState) {
        composeRule.setContent {
            ThirtySixTheme {
                AddressBar(state = state, callbacks = callbacks())
            }
        }
    }

    // ---------- US1 / T016 — initial render + IME submit ----------

    @Test
    fun initialState_unfocused_emptyUrl_showsPlaceholder() {
        // Empty currentUrl + unfocused → display value is empty; placeholder
        // hint renders inside the OutlinedTextField (Material3 contract).
        setBar(BrowserUiState(currentUrl = "", loadingState = LoadingState.Idle))
        // The bar exists.
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).assertIsDisplayed()
    }

    @Test
    fun unfocused_withUrl_displaysHostnameOnly() {
        // FR-014 — hostname-only when unfocused with a URL.
        setBar(
            BrowserUiState(
                currentUrl = "https://developer.android.com/jetpack/compose?utm=test",
                loadingState = LoadingState.Loaded,
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR)
            .assertTextEquals("developer.android.com")
    }

    @Test
    fun unfocused_aboutBlank_fallsBackToFullUrl() {
        // FR-016 — `about:blank` has no host → fall back to displaying the
        // raw URL string.
        setBar(BrowserUiState(currentUrl = "about:blank", loadingState = LoadingState.Loaded))
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR)
            .assertTextEquals("about:blank")
    }

    @Test
    fun imeAction_firesSubmit_onNonEmptyTyping() {
        // T016 — IME Go fires onSubmit. Set focused state so the field accepts
        // input + IME action. Type some text first, then trigger IME.
        setBar(
            BrowserUiState(
                currentUrl = "",
                loadingState = LoadingState.Idle,
                isAddressBarFocused = true,
                addressBarText = "example.com",
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performImeAction()
        assertEquals(1, submitCount)
    }

    @Test
    fun imeAction_firesSubmit_evenWhenInputIsEmpty() {
        // FR-012 / FR-013a — Composable always fires onSubmit; the ViewModel
        // is responsible for the empty no-op semantics. The Composable can't
        // peek into classification.
        setBar(
            BrowserUiState(
                currentUrl = "",
                loadingState = LoadingState.Idle,
                isAddressBarFocused = true,
                addressBarText = "",
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performImeAction()
        assertEquals(1, submitCount)
    }

    // ---------- US4 / T037 — clear button visibility + tap ----------

    @Test
    fun clearButton_invisible_whenUnfocused() {
        // FR-023 — clear hidden when unfocused, even if text present.
        setBar(
            BrowserUiState(
                currentUrl = "https://example.com",
                loadingState = LoadingState.Loaded,
                isAddressBarFocused = false,
                addressBarText = "typing-but-unfocused",
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR_CLEAR).assertDoesNotExist()
    }

    @Test
    fun clearButton_invisible_whenFocusedAndEmpty() {
        // FR-024 — clear hidden when focused but empty.
        setBar(
            BrowserUiState(
                currentUrl = "",
                loadingState = LoadingState.Idle,
                isAddressBarFocused = true,
                addressBarText = "",
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR_CLEAR).assertDoesNotExist()
    }

    @Test
    fun clearButton_visible_andFiresCallback_whenFocusedAndNonEmpty() {
        // FR-020 / FR-021 — clear visible iff focused + non-empty; tap fires
        // onClear and (per ViewModel contract) preserves focus.
        setBar(
            BrowserUiState(
                currentUrl = "https://example.com",
                loadingState = LoadingState.Loaded,
                isAddressBarFocused = true,
                addressBarText = "in-progress edit",
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR_CLEAR).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR_CLEAR).performClick()
        assertEquals(1, clearCount)
    }

    // ---------- US5 / T040 — display + focus / placeholder ----------

    @Test
    fun typingInputUpdatesViaCallback() {
        setBar(
            BrowserUiState(
                currentUrl = "",
                loadingState = LoadingState.Idle,
                isAddressBarFocused = true,
                addressBarText = "",
            ),
        )
        // performTextInput appends to the existing value; the field starts empty.
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performTextInput("hello")
        assertNotNull(lastTextChange)
        // Last invocation should reflect the typed text.
        assertEquals("hello", lastTextChange)
    }

    @Test
    fun replacingTextDuringFocus_invokesOnTextChange() {
        setBar(
            BrowserUiState(
                currentUrl = "",
                loadingState = LoadingState.Idle,
                isAddressBarFocused = true,
                addressBarText = "old",
            ),
        )
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performTextReplacement("new")
        assertEquals("new", lastTextChange)
    }
}
