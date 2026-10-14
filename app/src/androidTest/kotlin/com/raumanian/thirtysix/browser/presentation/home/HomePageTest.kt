package com.raumanian.thirtysix.browser.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.model.GreetingPeriod
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.model.HomeShortcut
import com.raumanian.thirtysix.browser.domain.model.ShortcutKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 019 T056 — the Home page (FR-011, FR-013, FR-014). Drives the stateless [HomeContent]. */
@RunWith(AndroidJUnit4::class)
class HomePageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setHome(state: HomeUiState) {
        composeRule.setContent {
            ThirtySixTheme {
                HomeContent(
                    state = state,
                    callbacks = HomeCallbacks(
                        onSearchSubmit = { calls += "search:$it" },
                        onOpenUrl = { calls += "open:$it" },
                        onSeeAllHistory = { calls += "seeAll" },
                        onNewIncognitoTab = { calls += "incognito" },
                    ),
                    onShortcut = { calls += "shortcut:${it.stableKey}" },
                    searchFocus = remember { FocusRequester() },
                    bottomPadding = 0.dp,
                )
            }
        }
    }

    @Test
    fun greeting_followsPeriod() {
        var state by mutableStateOf(HomeUiState(greeting = GreetingPeriod.Morning))
        composeRule.setContent {
            ThirtySixTheme {
                HomeContent(
                    state = state,
                    callbacks = HomeCallbacks({}, {}, {}, {}),
                    onShortcut = {},
                    searchFocus = remember { FocusRequester() },
                    bottomPadding = 0.dp,
                )
            }
        }
        listOf(
            GreetingPeriod.Morning to R.string.home_greeting_morning,
            GreetingPeriod.Afternoon to R.string.home_greeting_afternoon,
            GreetingPeriod.Evening to R.string.home_greeting_evening,
        ).forEach { (period, res) ->
            state = HomeUiState(greeting = period)
            composeRule.onNodeWithText(context.getString(res)).assertExists()
        }
    }

    @Test
    fun emptyState_showsNoRecentSectionAndNoCards() {
        setHome(HomeUiState(greeting = GreetingPeriod.Morning))
        composeRule.onNodeWithTag(TEST_TAG_HOME_GREETING).assertExists()
        composeRule.onNodeWithTag(TEST_TAG_HOME_RECENT_TITLE).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.home_shortcut_tag_tab)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.home_shortcut_tag_saved)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.home_shortcut_tag_recent)).assertDoesNotExist()
    }

    @Test
    fun tappingCardRowAndSeeAll_invokeTheirCallbacks() {
        val shortcut = HomeShortcut(
            stableKey = "bookmark:7",
            kind = ShortcutKind.Saved,
            title = "Saved Site",
            url = "https://saved.test/",
            host = "saved.test",
        )
        val entry = HistoryEntry(id = 3, url = "https://recent.test/", title = "Recent Page", visitedAt = 1L)
        setHome(
            HomeUiState(
                greeting = GreetingPeriod.Afternoon,
                shortcuts = listOf(HomeShortcutUi(shortcut)),
                recent = listOf(entry),
            ),
        )
        composeRule.onNodeWithText("Saved Site").performClick()
        composeRule.onNodeWithText("Recent Page").performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.home_recent_see_all)).performScrollTo().performClick()
        assertEquals(listOf("shortcut:bookmark:7", "open:https://recent.test/", "seeAll"), calls)
    }
}
