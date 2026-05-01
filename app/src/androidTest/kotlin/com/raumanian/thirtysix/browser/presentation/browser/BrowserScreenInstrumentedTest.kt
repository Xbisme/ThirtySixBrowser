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
 * Spec 008 (T023a) — decoupled from the production [UrlConstants.DEFAULT_HOME_URL]
 * value. Spec 008 changes that constant from `https://example.com` →
 * `https://www.google.com/`; this test continues to assert against the
 * `<h1>Example Domain</h1>` DOM element by constructing a [BrowserViewModel]
 * directly with the literal `https://example.com` URL (mirrors
 * `BrowserScreenOfflineErrorTest` which uses the same manual-VM-construction
 * pattern instead of `@TestInstallIn`-based Hilt module replacement).
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

    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            // Spec 008 (T023a) — manual VM construction with the literal
            // example.com URL keeps assertions on `<h1>Example Domain</h1>`
            // stable regardless of UrlConstants.DEFAULT_HOME_URL value
            // (Spec 008 changed the production constant to Google). VM is
            // class-scoped so the rotation test can re-attach it after
            // activity recreate, preserving the same UiState.
            viewModel = BrowserViewModel(defaultHomeUrl = TEST_PAGE_URL)
            composeRule.activity.setContent { BrowserScreen(viewModel = viewModel) }
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
    fun loadingIndicator_appearsAndHidesOnFinish() {
        // SC-002 (production): indicator visible within 200 ms of load start
        // event (`WebChromeClient.onProgressChanged` first tick). That budget
        // measures pure Compose recomposition latency on a real device and is
        // verified manually via Gate 7 step 3.
        //
        // THIS test runs on emulator API 29 and times the assertion from
        // `setContent` — it includes WebView initialization, emulator network
        // bring-up, and the JNI ↔ webkit handoff before `onProgressChanged`
        // fires. Empirically that overhead is hundreds of milliseconds on a
        // cold AVD, which is irrelevant to SC-002. Budget here is generous
        // enough not to flake on slow CI runners while still catching a
        // regression where the indicator never appears at all.
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = LOADING_INDICATOR_FIRST_SHOW_TIMEOUT_MS,
        )
        // Indicator must hide once the page finishes loading. SC-001 budget.
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_BROWSER_LOADING_INDICATOR),
            timeoutMillis = LOADING_INDICATOR_HIDE_TIMEOUT_MS,
        )
    }

    @Test
    fun rotation_preservesCurrentUrlInUiState() {
        // T021b — SC-004: simulate configuration change. The same VM instance
        // is re-attached after recreate (Spec 008 T023a refactor); its
        // MutableStateFlow holds currentUrl, so the WebView re-renders the
        // same URL successfully after activity recreation.
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { BrowserScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()

        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "h1"))
            .check(webMatches(getText(), containsString("Example Domain")))
    }

    private companion object {
        // Spec 008 (T023a) — pinned to https://example.com for the "Example Domain"
        // h1 assertion, decoupled from production UrlConstants.DEFAULT_HOME_URL
        // which Spec 008 redirected to Google homepage.
        const val TEST_PAGE_URL: String = "https://example.com"

        // CI emulator budget — NOT the user-facing SC-002 production target
        // (200 ms). See test method KDoc above.
        const val LOADING_INDICATOR_FIRST_SHOW_TIMEOUT_MS: Long = 5_000L
        const val LOADING_INDICATOR_HIDE_TIMEOUT_MS: Long = 10_000L
    }
}
