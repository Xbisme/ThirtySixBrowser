package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Home invitation's two controls. */
@RunWith(AndroidJUnit4::class)
class DefaultBrowserInviteCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theActionAndTheDismissButtonCallTheirOwnCallbacks() {
        var actions = 0
        var dismissals = 0
        composeRule.setContent {
            ThirtySixTheme { DefaultBrowserInviteCard(onAction = { actions++ }, onDismiss = { dismissals++ }) }
        }

        composeRule.onNodeWithTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_ACTION).performClick()
        assertEquals(1, actions)
        assertEquals("the action must not dismiss", 0, dismissals)

        composeRule.onNodeWithTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_DISMISS).performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun theDismissButtonIsLabelledForScreenReaders() {
        composeRule.setContent { ThirtySixTheme { DefaultBrowserInviteCard(onAction = {}, onDismiss = {}) } }

        composeRule.onNodeWithTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_DISMISS, useUnmergedTree = true)
            .assertContentDescriptionEquals(context.getString(R.string.home_default_browser_invite_dismiss))
    }
}
