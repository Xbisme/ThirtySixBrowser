package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarksScreenSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun oneCharacterQuery_hidesNoMatchesText() {
        setScreen("k")

        composeRule.onNodeWithText(noMatches("k")).assertDoesNotExist()
    }

    @Test
    fun twoCharacterQueryWithoutResults_showsNoMatchesText() {
        setScreen("ko")

        composeRule.onNodeWithText(noMatches("ko")).assertIsDisplayed()
    }

    private fun noMatches(query: String) = context.getString(R.string.bookmarks_no_search_matches, query)

    private fun setScreen(query: String) {
        composeRule.setContent {
            ThirtySixTheme {
                BookmarksScreen(
                    state = BookmarksUiState(isSearchActive = true, searchQuery = query),
                    snackbarHostState = SnackbarHostState(),
                    onAction = {},
                    onExitScreen = {},
                )
            }
        }
    }
}
