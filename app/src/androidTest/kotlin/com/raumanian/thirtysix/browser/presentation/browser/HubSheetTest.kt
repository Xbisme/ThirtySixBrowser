package com.raumanian.thirtysix.browser.presentation.browser

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.presentation.browser.components.HubCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.HubSheet
import com.raumanian.thirtysix.browser.presentation.browser.components.QuickSetting
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_HUB_BACK
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_HUB_DOWNLOADS_ACTIVE
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_HUB_FORWARD
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_HUB_SETTINGS
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 019 T036 — the hub (FR-030 – FR-032); replaces Spec 015's BrowserOverflowMenuTest. */
@RunWith(AndroidJUnit4::class)
class HubSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private var dismissed = 0

    private fun setHub(canGoBack: Boolean = true, canGoForward: Boolean = false, downloading: Boolean = false) {
        composeRule.setContent {
            ThirtySixTheme {
                HubSheet(
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    hasActiveDownloadsFlow = MutableStateFlow(downloading),
                    onDismiss = { dismissed++ },
                    callbacks = HubCallbacks(
                        onSettings = { calls += "settings" },
                        onBack = { calls += "back" },
                        onForward = { calls += "forward" },
                        onDownloadsInProgress = { calls += "downloadsActive" },
                        onPrivacy = { calls += "privacy" },
                        onNewIncognitoTab = { calls += "incognito" },
                        onDownloads = { calls += "downloads" },
                        onHistory = { calls += "history" },
                        onBookmarks = { calls += "bookmarks" },
                        onQuickSetting = { calls += "quick:$it" },
                    ),
                )
            }
        }
    }

    private fun awaitCall(expected: String) {
        composeRule.waitUntil(TIMEOUT_MS) { calls.isNotEmpty() }
        assertEquals(listOf(expected), calls)
        assertTrue("the hub closes before acting (FR-032)", dismissed >= 1)
    }

    @Test
    fun backAndForward_followPageHistory() {
        setHub(canGoBack = true, canGoForward = false)
        composeRule.onNodeWithTag(TEST_TAG_HUB_BACK).assertIsEnabled()
        composeRule.onNodeWithTag(TEST_TAG_HUB_FORWARD).assertIsNotEnabled()
    }

    @Test
    fun downloadsInProgressButton_onlyWhileSomethingDownloads() {
        setHub(downloading = false)
        composeRule.onNodeWithTag(TEST_TAG_HUB_DOWNLOADS_ACTIVE).assertDoesNotExist()
    }

    @Test
    fun downloadsInProgressButton_showsWhileDownloading() {
        setHub(downloading = true)
        composeRule.onNodeWithTag(TEST_TAG_HUB_DOWNLOADS_ACTIVE).performClick()
        awaitCall("downloadsActive")
    }

    @Test
    fun settings_closesTheSheetThenNavigates() {
        setHub()
        composeRule.onNodeWithTag(TEST_TAG_HUB_SETTINGS).performClick()
        awaitCall("settings")
    }

    @Test
    fun historyTile_reachesHistory() {
        setHub()
        composeRule.onNodeWithText("History").performScrollTo().performClick()
        awaitCall("history")
    }

    @Test
    fun themeTile_opensTheThemeChooser() {
        setHub()
        composeRule.onNodeWithText("Theme").performScrollTo().performClick()
        awaitCall("quick:${QuickSetting.Theme}")
    }

    @Test
    fun incognitoBanner_opensAnIncognitoTab() {
        setHub()
        composeRule.onNodeWithText("Browse without a trace").performScrollTo().performClick()
        awaitCall("incognito")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
