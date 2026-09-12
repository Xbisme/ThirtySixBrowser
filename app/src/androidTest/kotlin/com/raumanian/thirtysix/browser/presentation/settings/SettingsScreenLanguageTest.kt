package com.raumanian.thirtysix.browser.presentation.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.presentation.settings.components.TEST_TAG_SETTINGS_CHOICE_OPTION
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T061 (US3 / FR-003, FR-013, FR-014).
 *
 * Drives [SettingsScreen] over an in-memory language controller, so no test ever recreates its
 * own activity. The platform round trip itself is covered on device by quickstart G4.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenLanguageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(controller: InstrumentedFakeAppLanguageController) {
        composeRule.setContent {
            ThirtySixTheme { SettingsScreen(viewModel = settingsViewModel(appLanguageController = controller)) }
        }
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun languageRow_showsTheCurrentLanguage() {
        render(InstrumentedFakeAppLanguageController(currentLanguage = AppLanguage.Japanese))

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LANGUAGE_ROW).assertTextContains(JAPANESE_ENDONYM)
    }

    /** FR-014 — the eight names are the endonyms whatever language the test device runs in. */
    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun chooser_listsFollowSystemAndTheEightEndonyms() {
        render(InstrumentedFakeAppLanguageController())

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LANGUAGE_ROW).performClick()

        composeRule.onAllNodesWithTag(TEST_TAG_SETTINGS_CHOICE_OPTION).assertCountEquals(AppLanguage.entries.size)
        EXPECTED_ENDONYMS.forEach { endonym ->
            composeRule.onNode(hasTestTag(TEST_TAG_SETTINGS_CHOICE_OPTION) and hasText(endonym))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun selectingALanguage_reachesThePlatformSeam() {
        val controller = InstrumentedFakeAppLanguageController()
        render(controller)

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LANGUAGE_ROW).performClick()
        composeRule.onNode(hasTestTag(TEST_TAG_SETTINGS_CHOICE_OPTION) and hasText(GERMAN_ENDONYM))
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(SETTINGS_TEST_TIMEOUT_MS) { controller.applied.toList() == listOf(AppLanguage.German) }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_LANGUAGE_ROW).assertTextContains(GERMAN_ENDONYM)
    }

    private companion object {
        const val JAPANESE_ENDONYM = "日本語"
        const val GERMAN_ENDONYM = "Deutsch"
        val EXPECTED_ENDONYMS = listOf(
            "English",
            "Tiếng Việt",
            GERMAN_ENDONYM,
            "Русский",
            "한국어",
            JAPANESE_ENDONYM,
            "中文",
            "Français",
        )
    }
}
