package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBookmarkTransferRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun theRow_opensBookmarkTransfer() {
        var opened = 0
        composeRule.setContent {
            ThirtySixTheme {
                SettingsRoute(
                    viewModel = settingsViewModel(),
                    destinations = SettingsDestinations(onBookmarkTransfer = { opened++ }),
                )
            }
        }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LIST)
            .performScrollToNode(hasTestTag(TEST_TAG_SETTINGS_BOOKMARK_TRANSFER_ROW))
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_BOOKMARK_TRANSFER_ROW)
            .assertTextContains(context.getString(R.string.settings_bookmark_transfer_title))
            .performClick()

        composeRule.runOnIdle { assertEquals(1, opened) }
    }
}
