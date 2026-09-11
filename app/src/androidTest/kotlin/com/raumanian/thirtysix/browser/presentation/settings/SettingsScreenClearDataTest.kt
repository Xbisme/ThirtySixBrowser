package com.raumanian.thirtysix.browser.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CLEAR_CATEGORY_PREFIX
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CLEAR_CONFIRM
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CLEAR_DIALOG
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CLEAR_PROGRESS
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T077 (US4 / FR-025, FR-026, FR-032).
 *
 * Drives [SettingsScreen] over in-memory seams, so nothing real is ever cleared. What gets
 * cleared on a real web engine is covered on device by quickstart G5 and G6.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenClearDataTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(webDataCleaner: WebDataCleaner = InstrumentedFakeWebDataCleaner()) {
        composeRule.setContent {
            ThirtySixTheme { SettingsScreen(viewModel = settingsViewModel(webDataCleaner = webDataCleaner)) }
        }
    }

    private fun openClearDialog() {
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LIST).performScrollToNode(hasTestTag(TEST_TAG_SETTINGS_CLEAR_ROW))
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_ROW).performClick()
    }

    private fun categoryRow(category: ClearBrowsingDataCategory) =
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_CATEGORY_PREFIX + category.name)

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun dialog_opensWithAllThreeCategoriesChecked() {
        render()
        openClearDialog()

        ClearBrowsingDataCategory.entries.forEach { categoryRow(it).assertIsOn() }
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_CONFIRM).assertIsEnabled()
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun deselectingEveryCategory_disablesConfirm() {
        render()
        openClearDialog()

        ClearBrowsingDataCategory.entries.forEach { categoryRow(it).performClick() }

        ClearBrowsingDataCategory.entries.forEach { categoryRow(it).assertIsOff() }
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_CONFIRM).assertIsNotEnabled()
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun aRunningClear_cannotBeDismissedOrResubmitted() {
        val gate = CompletableDeferred<Unit>()
        val cleaner = InstrumentedFakeWebDataCleaner(gate)
        render(cleaner)
        openClearDialog()

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_CONFIRM).performClick()
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_PROGRESS).assertIsDisplayed()

        // FR-032 — system back must not close a clear that is still running.
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_CLEAR_CONFIRM).assertIsNotEnabled()

        gate.complete(Unit)
        composeRule.waitUntil(SETTINGS_TEST_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(TEST_TAG_SETTINGS_CLEAR_DIALOG).fetchSemanticsNodes().isEmpty()
        }
        check(cleaner.calls.count { it == InstrumentedFakeWebDataCleaner.CALL_SITE_DATA } == 1) {
            "the clear must run exactly once, calls=${cleaner.calls}"
        }
    }
}
