package com.raumanian.thirtysix.browser.presentation.browser

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserOverflowMenu
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserOverflowMenuCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_OVERFLOW_BOOKMARKS
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_OVERFLOW_DOWNLOADS
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_OVERFLOW_HISTORY
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_OVERFLOW_SETTINGS
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 015 T074 (US3 / FR-042 – FR-046), extended by Spec 016 T029 (FR-001).
 *
 * Bookmarks and History lost their bottom-bar buttons to this menu, so these assertions are
 * the regression net for two already-shipped features as much as they are coverage for the
 * entries added since.
 */
@RunWith(AndroidJUnit4::class)
class BrowserOverflowMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var bookmarks = 0
    private var history = 0
    private var downloads = 0
    private var settings = 0
    private var dismissed = 0

    private fun render(expanded: Boolean = true) {
        val state = mutableStateOf(expanded)
        composeRule.setContent {
            ThirtySixTheme {
                BrowserOverflowMenu(
                    expanded = state.value,
                    onDismiss = {
                        dismissed++
                        state.value = false
                    },
                    callbacks = BrowserOverflowMenuCallbacks(
                        onBookmarksClick = { bookmarks++ },
                        onHistoryClick = { history++ },
                        onDownloadsClick = { downloads++ },
                        onSettingsClick = { settings++ },
                    ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun openMenu_listsTheThreeCollectionsAndSettings() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_BOOKMARKS).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_HISTORY).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_DOWNLOADS).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun settingsEntry_isListedFourthAfterDownloads() {
        render()

        val downloadsBounds = composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_DOWNLOADS).getUnclippedBoundsInRoot()
        val settingsBounds = composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_SETTINGS).getUnclippedBoundsInRoot()

        assertTrue(
            "Spec 016 FR-001 — Settings must sit directly below Downloads",
            settingsBounds.top >= downloadsBounds.bottom,
        )
    }

    @Test
    fun bookmarksEntry_stillReachesBookmarks() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_BOOKMARKS).performClick()
        composeRule.waitForIdle()

        assertEquals("FR-044 — Spec 013's destination must survive the move", 1, bookmarks)
        assertEquals(0, history)
        assertEquals(0, downloads)
        assertEquals(0, settings)
    }

    @Test
    fun historyEntry_stillReachesHistory() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_HISTORY).performClick()
        composeRule.waitForIdle()

        assertEquals("FR-044 — Spec 014's destination must survive the move", 1, history)
        assertEquals(0, bookmarks)
    }

    @Test
    fun downloadsEntry_reachesDownloads() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_DOWNLOADS).performClick()
        composeRule.waitForIdle()

        assertEquals(1, downloads)
    }

    @Test
    fun settingsEntry_reachesSettings() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_SETTINGS).performClick()
        composeRule.waitForIdle()

        assertEquals("Spec 016 FR-001", 1, settings)
        assertEquals(0, downloads)
    }

    @Test
    fun collapsedMenu_rendersNothing() {
        render(expanded = false)

        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_BOOKMARKS).assertDoesNotExist()
        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_DOWNLOADS).assertDoesNotExist()
        composeRule.onNodeWithTag(TEST_TAG_OVERFLOW_SETTINGS).assertDoesNotExist()
    }
}
