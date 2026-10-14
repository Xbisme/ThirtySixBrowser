package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.feature.browser.domain.model.SuggestionSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The suggestion list on its own: rows, title fallback, tap, empty. */
@RunWith(AndroidJUnit4::class)
class AddressSuggestionListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = MutableStateFlow(AddressSuggestionsUiState())
    private val picked = mutableListOf<String>()

    private fun setList() = composeRule.setContent {
        ThirtySixTheme { AddressSuggestionList(stateFlow = state, onPick = { picked += it.url }) }
    }

    @Test
    fun rows_showTitleOrHostname_andTheAddress() {
        state.value = AddressSuggestionsUiState(
            listOf(
                AddressSuggestion("https://kotlinlang.org/docs", "Kotlin docs", SuggestionSource.Bookmark),
                AddressSuggestion("https://news.example/kotlin", "", SuggestionSource.History),
            ),
        )
        setList()
        composeRule.onAllNodesWithTag(TEST_TAG_ADDRESS_SUGGESTION_ROW).assertCountEquals(2)
        composeRule.onNodeWithText("Kotlin docs", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("news.example", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("https://news.example/kotlin", useUnmergedTree = true).assertExists()
    }

    @Test
    fun tap_reportsTheAddress() {
        state.value = AddressSuggestionsUiState(
            listOf(AddressSuggestion("https://kotlinlang.org/docs", "Kotlin docs", SuggestionSource.Bookmark)),
        )
        setList()
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_SUGGESTION_ROW).performClick()
        assertEquals(listOf("https://kotlinlang.org/docs"), picked)
    }

    @Test
    fun noSuggestions_drawsNothing() {
        setList()
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_SUGGESTIONS).assertDoesNotExist()
    }
}
