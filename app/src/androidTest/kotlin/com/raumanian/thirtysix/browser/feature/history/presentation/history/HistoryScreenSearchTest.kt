package com.raumanian.thirtysix.browser.feature.history.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.TEST_TAG_HISTORY_NO_MATCHES
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.TEST_TAG_HISTORY_ROW_PREFIX
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.TEST_TAG_HISTORY_SEARCH_CLEAR
import com.raumanian.thirtysix.browser.feature.history.presentation.history.component.TEST_TAG_HISTORY_SEARCH_FIELD
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val yesterday = now - TimeUnit.DAYS.toMillis(1)

    private val seeded = listOf(
        HistoryEntry(1L, "https://example.com/", "Example Domain", now),
        HistoryEntry(2L, "https://kotlinlang.org/", "Kotlin docs", yesterday),
    )

    @Test(timeout = TEST_TIMEOUT_MS)
    fun singleCharacterQuery_doesNotFilter() {
        setScreen(InstrumentedFakeHistoryRepository(seeded))

        typeQuery("k")

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "1").assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "2").assertIsDisplayed()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun twoCharacterQuery_filtersAndHidesZeroMatchDayHeaders() {
        setScreen(InstrumentedFakeHistoryRepository(seeded))

        // "ko" matches only the Kotlin entry, which lives under "Yesterday".
        typeQuery("ko")

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "1").assertDoesNotExist()
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "2").assertIsDisplayed()
        // The "Today" group has no matches, so its header leaves the tree.
        composeRule.onNodeWithText(TODAY_HEADER).assertDoesNotExist()
        composeRule.onNodeWithText(YESTERDAY_HEADER).assertIsDisplayed()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun nonMatchingQuery_showsNoMatchesState() {
        setScreen(InstrumentedFakeHistoryRepository(seeded))

        typeQuery("xyznomatchhere")

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_NO_MATCHES).assertIsDisplayed()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun clearingQuery_restoresTheFullList() {
        setScreen(InstrumentedFakeHistoryRepository(seeded))
        typeQuery("xyznomatchhere")
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_NO_MATCHES).assertIsDisplayed()

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_SEARCH_CLEAR).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "1").assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "2").assertIsDisplayed()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun newMatchingEntry_joinsTheFilteredListLive() {
        val repository = InstrumentedFakeHistoryRepository(seeded)
        setScreen(repository)
        typeQuery("kotlin")
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "2").assertIsDisplayed()

        runBlocking {
            repository.recordVisit("https://play.kotlinlang.org/", "Kotlin playground", now)
        }

        // Appears without the user re-triggering the search.
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "3")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "3").assertIsDisplayed()
    }

    private fun setScreen(repository: InstrumentedFakeHistoryRepository) {
        composeRule.setContent {
            ThirtySixTheme { HistoryScreen(viewModel = buildHistoryViewModel(repository)) }
        }
    }

    private fun typeQuery(query: String) {
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_SEARCH_FIELD).performTextReplacement(query)
        composeRule.waitForIdle()
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 30_000L
        const val TODAY_HEADER = "Today"
        const val YESTERDAY_HEADER = "Yesterday"
    }
}
