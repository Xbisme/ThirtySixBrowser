package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The capability flag is injected, so both branches run on any test device.
@RunWith(AndroidJUnit4::class)
class SettingsScreenDynamicColorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun dynamicColorRow_isAbsentWhereDynamicColorIsUnsupported() {
        composeRule.setContent {
            ThirtySixTheme { SettingsRoute(viewModel = settingsViewModel(supportsDynamicColor = false)) }
        }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW).assertDoesNotExist()
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun togglingTheRow_writesTheNewValue() {
        val repository = InstrumentedFakeSettingsRepository()
        composeRule.setContent {
            ThirtySixTheme {
                SettingsRoute(
                    viewModel = settingsViewModel(settingsRepository = repository, supportsDynamicColor = true),
                )
            }
        }

        // Dynamic color starts off, so the toggle turns it on.
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW).assertIsOff()
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW).performClick()
        composeRule.waitUntil(SETTINGS_TEST_TIMEOUT_MS) { repository.current.isDynamicColorEnabled }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW).assertIsOn()
    }
}
