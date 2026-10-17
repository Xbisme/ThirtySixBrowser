package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The lane with a plain field: swipes switch, taps focus, and the accessibility actions are offered. */
@RunWith(AndroidJUnit4::class)
class TabSwipeLaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nextLabel = context.getString(R.string.browser_a11y_next_tab)
    private val previousLabel = context.getString(R.string.browser_a11y_previous_tab)
    private val actions = mutableListOf<TabSwipeAction>()
    private var focused = false

    private fun setLane(swipe: TabSwipeUiState) {
        composeRule.setContent {
            TabSwipeHarness(swipe = swipe, onAction = { actions += it }, onFocusChange = { focused = it })
        }
    }

    private fun lane() = composeRule.onNodeWithTag(TEST_TAG_TAB_SWIPE_LANE)

    @Test
    fun swipingTowardTheStartSwitchesToTheNextTab() {
        setLane(TabSwipeUiState(previous = preview(1), next = preview(3)))

        lane().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(TabSwipeAction.SwitchTo(3, announce = false)), actions)
    }

    @Test
    fun swipingTowardTheEndSwitchesToThePreviousTab() {
        setLane(TabSwipeUiState(previous = preview(1), next = preview(3)))

        lane().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf(TabSwipeAction.SwitchTo(1, announce = false)), actions)
    }

    @Test
    fun aTapFocusesTheFieldAndSwitchesNothing() {
        setLane(TabSwipeUiState(previous = preview(1), next = preview(3)))

        composeRule.onNodeWithTag(TEST_TAG_HARNESS_FIELD).performClick()
        composeRule.waitForIdle()

        assertTrue(focused)
        assertEquals(emptyList<TabSwipeAction>(), actions)
    }

    @Test
    fun aFocusedFieldNeverSwitches() {
        setLane(TabSwipeUiState(previous = preview(1), next = preview(3)))
        composeRule.onNodeWithTag(TEST_TAG_HARNESS_FIELD).performClick()

        lane().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(emptyList<TabSwipeAction>(), actions)
    }

    @Test
    fun fromTheFirstTabSwipingTowardTheStartReachesTheNext() {
        setLane(TabSwipeUiState(previous = null, next = preview(3)))

        lane().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(TabSwipeAction.SwitchTo(3, announce = false)), actions)
    }

    @Test
    fun aSwipeBackThenAhead() {
        var swipe by mutableStateOf(TabSwipeUiState(previous = preview(1), next = null))
        composeRule.setContent { TabSwipeHarness(swipe = swipe, onAction = { actions += it }) }

        lane().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        swipe = TabSwipeUiState(previous = null, next = preview(2))
        composeRule.waitForIdle()
        lane().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(
            listOf(TabSwipeAction.SwitchTo(1, announce = false), TabSwipeAction.SwitchTo(2, announce = false)),
            actions,
        )
    }

    @Test
    fun twoQuickSwipesNeverFocusTheField() {
        var swipe by mutableStateOf(TabSwipeUiState(previous = preview(2), next = null))
        composeRule.setContent {
            TabSwipeHarness(swipe = swipe, onAction = { actions += it }, onFocusChange = { focused = it })
        }

        lane().performTouchInput { swipeRight() }
        swipe = TabSwipeUiState(previous = preview(1), next = preview(3))
        lane().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(false, focused)
        assertEquals(2, actions.size)
    }

    @Test
    fun withNoTabOnThatSideTheSwipeReturns() {
        setLane(TabSwipeUiState(previous = preview(1), next = null))

        lane().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(emptyList<TabSwipeAction>(), actions)
    }

    @Test
    fun withTheSettingOffTheSwipeDoesNothingButTheActionsRemain() {
        setLane(TabSwipeUiState(previous = preview(1), next = preview(3), enabled = false))

        lane().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(emptyList<TabSwipeAction>(), actions)
        assertEquals(listOf(nextLabel, previousLabel), actionLabels())
    }

    @Test
    fun theAccessibilityActionsMatchTheSidesThatHaveTabsAndAnnounce() {
        setLane(TabSwipeUiState(previous = null, next = preview(3)))

        assertEquals(listOf(nextLabel), actionLabels())
        val next = composeRule.onNodeWithTag(TEST_TAG_HARNESS_FIELD).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        composeRule.runOnUiThread { next.action() }

        assertEquals(listOf(TabSwipeAction.SwitchTo(3, announce = true)), actions)
    }

    private fun actionLabels(): List<String> =
        composeRule.onNodeWithTag(TEST_TAG_HARNESS_FIELD).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
}
