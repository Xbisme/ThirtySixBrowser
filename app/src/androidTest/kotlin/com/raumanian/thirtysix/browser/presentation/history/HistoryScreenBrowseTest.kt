package com.raumanian.thirtysix.browser.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.presentation.history.components.TEST_TAG_HISTORY_ROW_PREFIX
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 014 T048 (US1 / FR-008, FR-009, FR-010).
 *
 * Pre-seeds a history list spanning today + yesterday and drives [HistoryScreen]
 * directly (no Hilt, no WebView) to verify:
 *  - day-section headers render for both buckets,
 *  - every seeded row is present,
 *  - tapping a row rewrites the active tab's URL (the "replace active tab" contract).
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenBrowseTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val yesterday = now - TimeUnit.DAYS.toMillis(1)

    private val seeded = listOf(
        HistoryEntry(1L, "https://newest.example.com/", "Newest page", now),
        HistoryEntry(2L, "https://older.example.com/", "Older page", now - 60_000L),
        HistoryEntry(3L, "https://yesterday.example.com/", "Yesterday page", yesterday),
    )

    private val activeTab = Tab(
        id = 1L,
        url = INSTRUMENTED_HOME_URL,
        title = "Home",
        position = 0,
        createdAt = 0L,
        lastActiveAt = 1L,
    )

    @Test(timeout = TEST_TIMEOUT_MS)
    fun seededHistory_rendersDayHeadersAndEveryRow() {
        val repository = InstrumentedFakeHistoryRepository(seeded)
        composeRule.setContent {
            ThirtySixTheme { HistoryScreen(viewModel = buildHistoryViewModel(repository)) }
        }

        composeRule.onNodeWithText(TODAY_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText(YESTERDAY_HEADER).assertIsDisplayed()
        seeded.forEach { entry ->
            composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + entry.id).assertIsDisplayed()
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun tappingRow_replacesTheActiveTabUrl() {
        val repository = InstrumentedFakeHistoryRepository(seeded)
        val tabRepository = InstrumentedFakeTabRepository(listOf(activeTab))
        composeRule.setContent {
            ThirtySixTheme {
                HistoryScreen(viewModel = buildHistoryViewModel(repository, tabRepository))
            }
        }

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "1").performClick()
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            tabRepository.tabs.single().url == "https://newest.example.com/"
        }

        assertEquals("https://newest.example.com/", tabRepository.tabs.single().url)
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 30_000L

        // Matches `history_day_today` / `history_day_yesterday` in the EN baseline.
        const val TODAY_HEADER = "Today"
        const val YESTERDAY_HEADER = "Yesterday"
    }
}
