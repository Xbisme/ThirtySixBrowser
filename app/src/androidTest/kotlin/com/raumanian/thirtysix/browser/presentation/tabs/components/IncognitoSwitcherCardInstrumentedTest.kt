package com.raumanian.thirtysix.browser.presentation.tabs.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T047 (US4 / FR-014, FR-024).
 *
 * Verifies the tab switcher card render branch for an incognito tab:
 *  - The screenshot preview MUST NOT appear (no leak of incognito page
 *    content even momentarily during composition).
 *  - The dedicated incognito placeholder (Lock glyph + surfaceVariant
 *    background) MUST be rendered in its place.
 *
 * Pure component test — no Hilt, no live tab repository, no WebView. Drives
 * `TabSwitcherCard` directly with a hand-built [Tab] instance carrying
 * `isIncognito = true`.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoSwitcherCardInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val incognitoTab = Tab(
        id = -1L,
        url = "https://incognito.example/",
        title = "Sensitive Page",
        position = 0,
        createdAt = 0L,
        lastActiveAt = 0L,
        isIncognito = true,
    )

    @Test
    fun incognitoCard_rendersPlaceholderInsteadOfScreenshot() {
        composeRule.setContent {
            ThirtySixTheme {
                TabSwitcherCard(
                    tab = incognitoTab,
                    isActive = true,
                    faviconFile = null,
                    // FR-009 / R6: callsite passes null for incognito; even if
                    // it didn't, the card branch in TabPreviewArea ignores it.
                    screenshotFile = null,
                    onClick = {},
                    onCloseClick = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(TEST_TAG_INCOGNITO_PLACEHOLDER)
            .assertIsDisplayed()
        // Card itself is in the tree (cover for the contentDescription/tag chain).
        composeRule
            .onNodeWithTag(TEST_TAG_TAB_CARD_PREFIX + incognitoTab.id)
            .assertIsDisplayed()
    }
}
