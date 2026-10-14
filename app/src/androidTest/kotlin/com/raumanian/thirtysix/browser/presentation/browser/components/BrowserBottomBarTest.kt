package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 019 T041 — the five-slot bottom bar (FR-020); replaces Spec 008's NavigationBottomBarTest. */
@RunWith(AndroidJUnit4::class)
class BrowserBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setBar(tabCount: Int = 3, incognito: Boolean = false) {
        composeRule.setContent {
            ThirtySixTheme {
                BrowserBottomBar(
                    tabCount = tabCount,
                    isIncognito = incognito,
                    isHomeActive = false,
                    callbacks = BrowserBottomBarCallbacks(
                        onHome = { calls += "home" },
                        onSearch = { calls += "search" },
                        onNewTab = { calls += "newTab" },
                        onTabs = { calls += "tabs" },
                        onTabsLongPress = { calls += "tabsLong" },
                        onMenu = { calls += "menu" },
                    ),
                )
            }
        }
    }

    @Test
    fun eachSlot_invokesItsOwnCallback() {
        setBar()
        listOf(
            TEST_TAG_NAV_HOME to "home",
            TEST_TAG_NAV_SEARCH to "search",
            TEST_TAG_NAV_NEW_TAB to "newTab",
            TEST_TAG_NAV_TABS to "tabs",
            TEST_TAG_NAV_MENU to "menu",
        ).forEach { (tag, expected) ->
            calls.clear()
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
            assertEquals(listOf(expected), calls)
        }
    }

    @Test
    fun longPressOnTabs_opensANewTabInline() {
        setBar()
        composeRule.onNodeWithTag(TEST_TAG_NAV_TABS).performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(listOf("tabsLong"), calls)
    }

    @Test
    fun tabsSlot_speaksTheOpenTabCount() {
        setBar(tabCount = 4)
        composeRule.onNodeWithTag(TEST_TAG_NAV_TABS).assertContentDescriptionContains("4", substring = true)
    }

    @Test
    fun thereIsNoBackButton() {
        setBar()
        // FR-020 / FR-021 — back is the system gesture; the bar never offers one.
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun incognitoBar_rendersTheSameFiveSlots() {
        setBar(incognito = true)
        composeRule.onNodeWithTag(TEST_TAG_NAV_MENU).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("menu"), calls)
    }
}
