package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gate 6 for the tab swipe: one full drag, many frames, and the pill's content and the page area
 * recompose at most once each. The offset is read only in layer blocks.
 */
@RunWith(AndroidJUnit4::class)
class TabSwipeRecompositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aDragRecomposesThePillAndThePageAtMostOnceEach() {
        val counts = RecompositionCounts()
        val actions = mutableListOf<TabSwipeAction>()
        composeRule.setContent {
            TabSwipeHarness(
                swipe = TabSwipeUiState(previous = preview(1), next = preview(3)),
                onAction = { actions += it },
                counts = counts,
            )
        }
        composeRule.waitForIdle()
        val pillBefore = counts.pill
        val pageBefore = counts.page

        composeRule.onNodeWithTag(TEST_TAG_TAB_SWIPE_LANE).performTouchInput {
            down(centerRight - Offset(STEP_PX, 0f))
            repeat(STEPS) {
                moveBy(Offset(-STEP_PX, 0f))
            }
            up()
        }
        composeRule.waitForIdle()

        val pill = counts.pill - pillBefore
        val page = counts.page - pageBefore
        assertEquals(listOf(TabSwipeAction.SwitchTo(3, announce = false)), actions)
        assertTrue("pill content recomposed $pill times", pill <= MAX_RECOMPOSITIONS)
        assertTrue("page area recomposed $page times", page <= MAX_RECOMPOSITIONS)
    }

    private companion object {
        const val STEPS = 30
        const val STEP_PX = 20f
        const val MAX_RECOMPOSITIONS = 1
    }
}
