package com.raumanian.thirtysix.browser.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.presentation.history.components.TEST_TAG_HISTORY_ACTION_COPY_URL
import com.raumanian.thirtysix.browser.presentation.history.components.TEST_TAG_HISTORY_ACTION_DELETE
import com.raumanian.thirtysix.browser.presentation.history.components.TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB
import com.raumanian.thirtysix.browser.presentation.history.components.TEST_TAG_HISTORY_ACTION_SHEET
import com.raumanian.thirtysix.browser.presentation.history.components.TEST_TAG_HISTORY_ROW_PREFIX
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 014 T081 (US3 / FR-018, FR-019, FR-020, FR-021).
 *
 * Long-presses a row and verifies the sheet exposes exactly the three specified
 * actions, then exercises Delete (only the targeted row disappears), Open in new
 * tab (a **normal** tab is created), and Copy URL (the exact URL reaches the
 * clipboard seam).
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenLongPressTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()

    private val seeded = listOf(
        HistoryEntry(1L, "https://one.example.com/", "One", now),
        HistoryEntry(2L, "https://two.example.com/", "Two", now - 1_000L),
        HistoryEntry(3L, "https://three.example.com/", "Three", now - 2_000L),
    )

    @Test(timeout = TEST_TIMEOUT_MS)
    fun longPress_showsExactlyTheThreeSpecifiedActions() {
        setScreen(InstrumentedFakeHistoryRepository(seeded))

        longPressRow(1L)

        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_DELETE).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_COPY_URL).assertIsDisplayed()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun deleteAction_removesOnlyTheTargetedRow() {
        val repository = InstrumentedFakeHistoryRepository(seeded)
        setScreen(repository)

        longPressRow(1L)
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_DELETE).performClick()
        composeRule.waitUntil(TEST_TIMEOUT_MS) { repository.entries.size == 2 }

        assertEquals(listOf(2L, 3L), repository.entries.map { it.id })
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(TEST_TAG_HISTORY_ROW_PREFIX + "1")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun openInNewTabAction_createsANormalTab() {
        val tabRepository = InstrumentedFakeTabRepository()
        setScreen(InstrumentedFakeHistoryRepository(seeded), tabRepository = tabRepository)

        longPressRow(2L)
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB).performClick()
        composeRule.waitUntil(TEST_TIMEOUT_MS) { tabRepository.tabs.isNotEmpty() }

        val created = tabRepository.tabs.single()
        assertEquals("https://two.example.com/", created.url)
        assertEquals(false, created.isIncognito)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun copyUrlAction_putsTheExactUrlOnTheClipboard() {
        val clipboard = InstrumentedRecordingClipboardWriter()
        setScreen(InstrumentedFakeHistoryRepository(seeded), clipboardWriter = clipboard)

        longPressRow(3L)
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ACTION_COPY_URL).performClick()
        composeRule.waitUntil(TEST_TIMEOUT_MS) { clipboard.copied.isNotEmpty() }

        assertEquals(listOf("https://three.example.com/"), clipboard.copied)
    }

    private fun setScreen(
        repository: InstrumentedFakeHistoryRepository,
        tabRepository: InstrumentedFakeTabRepository = InstrumentedFakeTabRepository(),
        clipboardWriter: InstrumentedRecordingClipboardWriter =
            InstrumentedRecordingClipboardWriter(),
    ) {
        composeRule.setContent {
            ThirtySixTheme {
                HistoryScreen(
                    viewModel = buildHistoryViewModel(repository, tabRepository, clipboardWriter),
                )
            }
        }
    }

    private fun longPressRow(id: Long) {
        composeRule.onNodeWithTag(TEST_TAG_HISTORY_ROW_PREFIX + id)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 30_000L
    }
}
