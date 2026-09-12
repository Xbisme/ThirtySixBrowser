package com.raumanian.thirtysix.browser.presentation.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CHOICE_OPTION
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T037 (US1 / FR-003, FR-006).
 *
 * Drives [SettingsScreen] over an in-memory repository — no Hilt, no WebView — and checks the
 * theme row, the chooser's options and selection, and that a choice reaches the row.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val light = context.getString(R.string.settings_theme_light)
    private val dark = context.getString(R.string.settings_theme_dark)

    private fun render(repository: InstrumentedFakeSettingsRepository) {
        composeRule.setContent {
            ThirtySixTheme { SettingsScreen(viewModel = settingsViewModel(settingsRepository = repository)) }
        }
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun themeRow_showsTheCurrentMode() {
        render(InstrumentedFakeSettingsRepository(UserSettings.DEFAULT.copy(themeMode = ThemeMode.Light)))

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_THEME_ROW).assertTextContains(light)
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun chooser_listsThreeOptionsWithTheCurrentOneSelected() {
        render(InstrumentedFakeSettingsRepository(UserSettings.DEFAULT.copy(themeMode = ThemeMode.Light)))

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_THEME_ROW).performClick()

        val options = composeRule.onAllNodesWithTag(TEST_TAG_SETTINGS_CHOICE_OPTION)
        options.assertCountEquals(ThemeMode.entries.size)
        options.filter(isSelected()).assertCountEquals(1)
        composeRule.onNode(hasTestTag(TEST_TAG_SETTINGS_CHOICE_OPTION) and isSelected()).assertTextContains(light)
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun choosingDark_updatesTheRow() {
        val repository = InstrumentedFakeSettingsRepository(UserSettings.DEFAULT.copy(themeMode = ThemeMode.Light))
        render(repository)

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_THEME_ROW).performClick()
        composeRule.onNode(hasTestTag(TEST_TAG_SETTINGS_CHOICE_OPTION) and hasText(dark)).performClick()
        composeRule.waitUntil(SETTINGS_TEST_TIMEOUT_MS) { repository.current.themeMode == ThemeMode.Dark }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_THEME_ROW).assertTextContains(dark)
        composeRule.onAllNodesWithTag(TEST_TAG_SETTINGS_CHOICE_OPTION).assertCountEquals(0)
    }
}
