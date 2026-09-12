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
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CHOICE_OPTION
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T044 (US2 / FR-003, FR-006).
 *
 * Drives [SettingsScreen] over an in-memory repository and checks the search-engine row, that
 * the chooser offers exactly the three engines, and that a choice reaches the row.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenSearchEngineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val google = context.getString(R.string.search_engine_name_google)
    private val bing = context.getString(R.string.search_engine_name_bing)

    private fun render(repository: InstrumentedFakeSettingsRepository) {
        composeRule.setContent {
            ThirtySixTheme { SettingsScreen(viewModel = settingsViewModel(settingsRepository = repository)) }
        }
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun searchEngineRow_showsTheCurrentEngine() {
        render(InstrumentedFakeSettingsRepository())

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW).assertTextContains(google)
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun chooser_listsExactlyTheThreeEngines() {
        render(InstrumentedFakeSettingsRepository())

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW).performClick()

        val options = composeRule.onAllNodesWithTag(TEST_TAG_SETTINGS_CHOICE_OPTION)
        options.assertCountEquals(SearchEngine.entries.size)
        options.filter(isSelected()).assertCountEquals(1)
        composeRule.onNode(hasTestTag(TEST_TAG_SETTINGS_CHOICE_OPTION) and isSelected()).assertTextContains(google)
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun choosingBing_updatesTheRow() {
        val repository = InstrumentedFakeSettingsRepository()
        render(repository)

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW).performClick()
        composeRule.onNode(hasTestTag(TEST_TAG_SETTINGS_CHOICE_OPTION) and hasText(bing)).performClick()
        composeRule.waitUntil(SETTINGS_TEST_TIMEOUT_MS) { repository.current.searchEngine == SearchEngine.Bing }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW).assertTextContains(bing)
    }
}
