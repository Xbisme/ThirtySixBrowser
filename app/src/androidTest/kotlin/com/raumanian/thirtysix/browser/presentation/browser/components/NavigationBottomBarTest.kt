package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 008 — component-level Compose UI test for [NavigationBottomBar].
 *
 * Covers tasks T027 (US1 Back/Forward enabled-disabled + click), T035 (US3
 * Reload/Stop semantic toggle), T039 (US4 Home always-enabled + click), and
 * T050 (FR-017 rapid-tap regression).
 *
 * Pure component test — no WebView, no Hilt, no real navigation. Drives the
 * bar with controlled state via parameters and asserts on click-lambda
 * invocation counts. Fast and deterministic on any emulator API level.
 */
@RunWith(AndroidJUnit4::class)
class NavigationBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Holders for click invocation counts.
    private var backCount = 0
    private var forwardCount = 0
    private var reloadOrStopCount = 0
    private var homeCount = 0
    private var tabsSwitcherClickCount = 0
    private var tabsSwitcherLongClickCount = 0

    private fun setBar(
        canGoBack: Boolean = false,
        canGoForward: Boolean = false,
        isLoading: Boolean = false,
        tabCount: Int = 0,
    ) {
        composeRule.setContent {
            ThirtySixTheme {
                NavigationBottomBar(
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isLoading = isLoading,
                    tabCount = tabCount,
                    callbacks = NavigationBottomBarCallbacks(
                        onBack = { backCount++ },
                        onForward = { forwardCount++ },
                        onReloadOrStop = { reloadOrStopCount++ },
                        onHome = { homeCount++ },
                        onTabsSwitcherClick = { tabsSwitcherClickCount++ },
                        onTabsSwitcherLongClick = { tabsSwitcherLongClickCount++ },
                    ),
                )
            }
        }
    }

    // ---------- US1 (T027) — Back / Forward enabled-disabled + click ----------

    @Test
    fun back_isDisabled_whenCanGoBackFalse() {
        setBar(canGoBack = false)
        composeRule.onNodeWithTag(TEST_TAG_NAV_BACK).assertIsNotEnabled()
    }

    @Test
    fun back_isEnabled_andClickable_whenCanGoBackTrue() {
        setBar(canGoBack = true)
        composeRule.onNodeWithTag(TEST_TAG_NAV_BACK)
            .assertIsEnabled()
            .performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun forward_isDisabled_whenCanGoForwardFalse() {
        setBar(canGoForward = false)
        composeRule.onNodeWithTag(TEST_TAG_NAV_FORWARD).assertIsNotEnabled()
    }

    @Test
    fun forward_isEnabled_andClickable_whenCanGoForwardTrue() {
        setBar(canGoForward = true)
        composeRule.onNodeWithTag(TEST_TAG_NAV_FORWARD)
            .assertIsEnabled()
            .performClick()
        assertEquals(1, forwardCount)
    }

    @Test
    fun back_andForward_areIndependent() {
        setBar(canGoBack = true, canGoForward = false)
        composeRule.onNodeWithTag(TEST_TAG_NAV_BACK).assertIsEnabled()
        composeRule.onNodeWithTag(TEST_TAG_NAV_FORWARD).assertIsNotEnabled()
    }

    // ---------- US3 (T035) — Reload/Stop combined affordance ----------

    @Test
    fun reloadOrStop_alwaysEnabledRegardlessOfHistoryState() {
        setBar(canGoBack = false, canGoForward = false, isLoading = false)
        composeRule.onNodeWithTag(TEST_TAG_NAV_RELOAD_STOP).assertIsEnabled()
    }

    @Test
    fun reloadOrStop_clickInvokesLambdaOnce_whenLoading() {
        setBar(isLoading = true)
        composeRule.onNodeWithTag(TEST_TAG_NAV_RELOAD_STOP).performClick()
        assertEquals(1, reloadOrStopCount)
    }

    @Test
    fun reloadOrStop_clickInvokesLambdaOnce_whenIdle() {
        setBar(isLoading = false)
        composeRule.onNodeWithTag(TEST_TAG_NAV_RELOAD_STOP).performClick()
        assertEquals(1, reloadOrStopCount)
    }

    // ---------- US4 (T039) — Home button ----------

    @Test
    fun home_isAlwaysEnabled_inIdleEmptyHistoryState() {
        // composeRule.setContent can only be called once per test, so the
        // "regardless of history state" assertion is split across two tests.
        setBar(canGoBack = false, canGoForward = false, isLoading = false)
        composeRule.onNodeWithTag(TEST_TAG_NAV_HOME).assertIsEnabled()
    }

    @Test
    fun home_isAlwaysEnabled_inLoadingFullHistoryState() {
        setBar(canGoBack = true, canGoForward = true, isLoading = true)
        composeRule.onNodeWithTag(TEST_TAG_NAV_HOME).assertIsEnabled()
    }

    @Test
    fun home_clickInvokesLambdaOnce() {
        setBar()
        composeRule.onNodeWithTag(TEST_TAG_NAV_HOME).performClick()
        assertEquals(1, homeCount)
    }

    // ---------- T050 — Rapid-tap regression (FR-017) ----------

    @Test
    fun rapidTap_back_doesNotCrash_andInvokesLambdaPerTap() {
        setBar(canGoBack = true)
        repeat(RAPID_TAP_COUNT) {
            composeRule.onNodeWithTag(TEST_TAG_NAV_BACK).performClick()
        }
        assertEquals(RAPID_TAP_COUNT, backCount)
    }

    @Test
    fun rapidTap_reloadOrStop_doesNotCrash_andInvokesLambdaPerTap() {
        setBar(isLoading = false)
        repeat(RAPID_TAP_COUNT) {
            composeRule.onNodeWithTag(TEST_TAG_NAV_RELOAD_STOP).performClick()
        }
        assertEquals(RAPID_TAP_COUNT, reloadOrStopCount)
    }

    // ---------- Spec 011 (T025) — 5th button: Tabs switcher + long-press ----------

    @Test
    fun renders_5_buttons_with_tabs_switcher() {
        setBar()
        composeRule.onNodeWithTag(TEST_TAG_NAV_BACK).assertIsNotEnabled()
        composeRule.onNodeWithTag(TEST_TAG_NAV_FORWARD).assertIsNotEnabled()
        composeRule.onNodeWithTag(TEST_TAG_NAV_RELOAD_STOP).assertIsEnabled()
        composeRule.onNodeWithTag(TEST_TAG_NAV_HOME).assertIsEnabled()
        // 5th button is a clickable Box wrapped in BadgedBox; it always
        // exists regardless of tab count.
        composeRule.onNodeWithTag(TEST_TAG_NAV_TABS_SWITCHER).assertExists()
    }

    @Test
    fun switcher_singleTap_invokesOnTabsSwitcherClick() {
        setBar(tabCount = 3)
        composeRule.onNodeWithTag(TEST_TAG_NAV_TABS_SWITCHER).performClick()
        assertEquals(1, tabsSwitcherClickCount)
        assertEquals(0, tabsSwitcherLongClickCount)
    }

    @Test
    fun switcher_longPress_invokesOnTabsSwitcherLongClick() {
        setBar(tabCount = 3)
        composeRule.onNodeWithTag(TEST_TAG_NAV_TABS_SWITCHER)
            .performTouchInput { longClick() }
        assertEquals(1, tabsSwitcherLongClickCount)
        assertEquals(0, tabsSwitcherClickCount)
    }

    @Test
    fun switcher_badge_reflectsTabCount() {
        setBar(tabCount = TEST_BADGE_COUNT)
        // BadgedBox renders its badge content as a child node containing the
        // count text. Search the subtree of the switcher button for the
        // text node and assert the value matches.
        composeRule.onNodeWithTag(TEST_TAG_NAV_TABS_SWITCHER)
            .onChildren()
            .filterToOne(hasText(TEST_BADGE_COUNT.toString()))
            .assertTextEquals(TEST_BADGE_COUNT.toString())
    }

    private companion object {
        const val RAPID_TAP_COUNT: Int = 10
        const val TEST_BADGE_COUNT: Int = 7
    }
}
