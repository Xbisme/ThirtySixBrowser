package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An incognito tab card shows the incognito placeholder and never the screenshot, not even
 * momentarily. Drives `TabSwitcherCard` directly, without Hilt or a WebView.
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

    @Test(timeout = TEST_TIMEOUT_MS)
    fun incognitoCard_rendersPlaceholderInsteadOfScreenshot() {
        // A fixed width gives the 16:9 preview a size; the default host's root is 0 wide,
        // which collapses the card.
        composeRule.setContent {
            ThirtySixTheme {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TabSwitcherCard(
                        tab = incognitoTab,
                        isActive = true,
                        faviconFile = null,
                        // Callers pass null for incognito; the card would ignore a file anyway.
                        screenshotFile = null,
                        onClick = {},
                        onCloseClick = {},
                    )
                }
            }
        }

        // The card's contentDescription merges its descendants, so the placeholder's tag
        // is only in the unmerged tree.
        composeRule
            .onNodeWithTag(TEST_TAG_INCOGNITO_PLACEHOLDER, useUnmergedTree = true)
            .assertExists()
        // Card itself sits at the top of the merged tree (it owns the merge
        // boundary), so its tag is reachable via the default merged finder.
        composeRule
            .onNodeWithTag(TEST_TAG_TAB_CARD_PREFIX + incognitoTab.id)
            .assertExists()
    }

    private companion object {
        const val TEST_TIMEOUT_MS: Long = 30_000L
    }
}
