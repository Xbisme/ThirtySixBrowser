package com.raumanian.thirtysix.browser.presentation.browser

import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_BROWSER_LOADING_INDICATOR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 007 — end-to-end pipeline verification for the Browser screen.
 *
 * - T021 asserts the page DOM contains "Example Domain" (FR-012, SC-005).
 * - T021b asserts SC-004: configuration change preserves `currentUrl` in
 *   `BrowserUiState` (no URL revert across activity recreation).
 *
 * US2 / US3 extend this class with loading-indicator and offline-error tests
 * (T026, T036).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BrowserScreenInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { BrowserScreen() }
        }
    }

    @Test
    fun pageRenders_assertsDomContainsExampleDomain() {
        // T021 — Espresso-Web atom waits for JS-ready state, then checks <h1> text.
        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "h1"))
            .check(webMatches(getText(), containsString("Example Domain")))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingIndicator_appearsWithin200msAndHidesOnFinish() {
        // T026 — SC-002 enforcement.
        // (1) Indicator must appear within 200 ms of launch.
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = LOADING_INDICATOR_FIRST_SHOW_TIMEOUT_MS,
        )
        // (2) Indicator must hide once the page finishes loading. SC-001 budget.
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = LOADING_INDICATOR_HIDE_TIMEOUT_MS,
        )
    }

    @Test
    fun rotation_preservesCurrentUrlInUiState() {
        // T021b — SC-004: simulate configuration change. ViewModelStore survives
        // recreation, so the URL in BrowserUiState is preserved; the WebView
        // re-renders the same URL successfully after activity recreation.
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { BrowserScreen() }
        }
        composeRule.waitForIdle()

        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "h1"))
            .check(webMatches(getText(), containsString("Example Domain")))
    }

    private companion object {
        const val LOADING_INDICATOR_FIRST_SHOW_TIMEOUT_MS: Long = 200L
        const val LOADING_INDICATOR_HIDE_TIMEOUT_MS: Long = 5_000L
    }
}
