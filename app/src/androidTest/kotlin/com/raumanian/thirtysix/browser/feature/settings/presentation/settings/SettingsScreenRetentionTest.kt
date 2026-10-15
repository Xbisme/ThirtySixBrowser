package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// What a confirmed change deletes is covered by ChangeHistoryRetentionUseCaseTest.
@RunWith(AndroidJUnit4::class)
class SettingsScreenRetentionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun label(retention: HistoryRetention): String =
        resources.getQuantityString(R.plurals.settings_history_retention_days, retention.days, retention.days)

    private fun render(repository: InstrumentedFakeSettingsRepository = InstrumentedFakeSettingsRepository()) {
        composeRule.setContent {
            ThirtySixTheme { SettingsScreen(viewModel = settingsViewModel(settingsRepository = repository)) }
        }
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LIST)
            .performScrollToNode(hasTestTag(TEST_TAG_SETTINGS_RETENTION_ROW))
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun retentionRow_showsNinetyDaysByDefault() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_RETENTION_ROW).assertTextContains(label(HistoryRetention.Days90))
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun chooser_listsExactlyTheFourWindows() {
        render()

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_RETENTION_ROW).performClick()

        composeRule.onAllNodes(isSettingsChoiceOption()).assertCountEquals(HistoryRetention.entries.size)
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun aShorterWindow_warnsFirst_andCancelLeavesTheRowUnchanged() {
        val repository = InstrumentedFakeSettingsRepository()
        render(repository)

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_RETENTION_ROW).performClick()
        composeRule.onNode(isSettingsChoiceOption() and hasText(label(HistoryRetention.Days30)))
            .performClick()
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG).assertIsDisplayed()

        composeRule.onNode(hasText(resources.getString(R.string.settings_action_cancel)) and hasClickAction())
            .performClick()

        composeRule.onAllNodesWithTag(TEST_TAG_SETTINGS_RETENTION_CONFIRM_DIALOG).assertCountEquals(0)
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_RETENTION_ROW).assertTextContains(label(HistoryRetention.Days90))
        check(repository.current.historyRetention == HistoryRetention.Days90) { "cancel must not save" }
    }
}
