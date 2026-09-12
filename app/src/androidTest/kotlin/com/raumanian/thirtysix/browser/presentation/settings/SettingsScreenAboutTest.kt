package com.raumanian.thirtysix.browser.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_ABOUT_PRIVACY
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_ABOUT_VERSION
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T101 (US7 / FR-034, FR-035).
 *
 * The version is injected, so the assertion compares against exactly what was passed in. The
 * on-device comparison with `dumpsys package` is quickstart G9.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenAboutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun about_showsTheInjectedVersionAndThePrivacyStatement() {
        composeRule.setContent {
            ThirtySixTheme {
                SettingsScreen(viewModel = settingsViewModel(appVersionName = INSTRUMENTED_APP_VERSION_NAME))
            }
        }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LIST).performScrollToNode(hasTestTag(TEST_TAG_SETTINGS_ABOUT))

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_ABOUT_VERSION)
            .assertTextContains(INSTRUMENTED_APP_VERSION_NAME, substring = true)
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_ABOUT_PRIVACY)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.settings_about_privacy_statement))
    }
}
