package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDefaultBrowserRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(isDefault: Boolean) {
        composeRule.setContent {
            ThirtySixTheme {
                SettingsScreen(viewModel = settingsViewModel(isDefaultBrowser = IsDefaultBrowserUseCase { isDefault }))
            }
        }
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun notDefault_offersTheAction() {
        render(isDefault = false)
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_DEFAULT_BROWSER_ROW)
            .assertTextContains(context.getString(R.string.settings_default_browser_summary_not_default))
            .assertHasClickAction()
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun isDefault_isInformationOnly() {
        render(isDefault = true)
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_DEFAULT_BROWSER_ROW)
            .assertTextContains(context.getString(R.string.settings_default_browser_summary_is_default))
            .assertHasNoClickAction()
    }
}
