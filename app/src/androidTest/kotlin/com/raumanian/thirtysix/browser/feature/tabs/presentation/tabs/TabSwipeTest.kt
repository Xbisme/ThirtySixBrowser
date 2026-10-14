package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component.TEST_TAG_TAB_CARD_PREFIX
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.component.TabSwitcherCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Swiping a tab card sideways closes it. */
@RunWith(AndroidJUnit4::class)
class TabSwipeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var closes = 0
    private val tab = Tab(
        id = 7L,
        url = "https://swipe.test/",
        title = "Swipe",
        position = 0,
        createdAt = 0L,
        lastActiveAt = 0L,
    )

    private fun setCard() {
        composeRule.setContent {
            ThirtySixTheme {
                TabSwitcherCard(
                    tab = tab,
                    isActive = false,
                    faviconFile = null,
                    screenshotFile = null,
                    onClick = {},
                    onCloseClick = { closes++ },
                    modifier = Modifier.width(CARD_WIDTH.dp),
                )
            }
        }
    }

    private val card get() = composeRule.onNodeWithTag(TEST_TAG_TAB_CARD_PREFIX + tab.id)

    @Test
    fun longSwipe_closesOnce() {
        setCard()
        card.performTouchInput { swipeLeft(startX = right - 1f, endX = left + 1f) }
        composeRule.waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun shortSwipe_springsBack() {
        setCard()
        card.performTouchInput { swipeRight(startX = centerX, endX = centerX + width * SHORT_FRACTION) }
        composeRule.waitForIdle()
        assertEquals(0, closes)
    }

    @Test
    fun verticalSwipe_doesNotClose() {
        setCard()
        card.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(0, closes)
    }

    private companion object {
        const val CARD_WIDTH = 180
        const val SHORT_FRACTION = 0.2f
    }
}
