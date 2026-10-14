package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 022 T053 / FR-025 — what the server sees: a desktop user agent from a tab built in desktop
 * mode, and the mobile one again after switching back (which reloads the page).
 */
@RunWith(AndroidJUnit4::class)
class DesktopModeInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val loadsFinished = AtomicInteger()
    private val actions = WebViewActionsHandle()

    @After
    fun tearDown() = server.close()

    private fun setWebView(desktop: Boolean) {
        val url = server.url(PAGE)
        composeRule.setContent {
            BrowserWebView(
                incognitoProfile = IncognitoProfiles,
                state = remember { BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle) },
                actions = actions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = {},
                    onProgressChanged = {},
                    onLoadFinished = { loadsFinished.incrementAndGet() },
                    onLoadFailed = {},
                    onDownloadRequested = { _, _, _, _ -> },
                ),
                navigationCallbacks = BrowserNavigationCallbacks(
                    onUrlChange = {},
                    onCanGoBackChange = {},
                    onCanGoForwardChange = {},
                    onTitleChange = {},
                    onIconReceived = { _, _ -> },
                    onScreenshotReady = {},
                ),
                modifier = Modifier.fillMaxSize(),
                desktopMode = desktop,
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) { loadsFinished.get() > 0 && pageLoads().isNotEmpty() }
    }

    /** Only the page itself: a favicon request from an earlier load can arrive late. */
    private fun pageLoads() = server.userAgentsFor(PAGE)

    private fun isMobile(userAgent: String) = userAgent.contains("Mobile") || userAgent.contains("; wv")

    @Test
    fun aTabBuiltInDesktopMode_sendsADesktopUserAgent() {
        setWebView(desktop = true)
        val sent = pageLoads().first()
        assertFalse(sent, isMobile(sent))
        assertTrue(sent, sent.contains(DesktopUserAgent.PLATFORM_TOKEN))
    }

    @Test
    fun switchingBackToMobile_reloadsWithTheMobileUserAgent() {
        setWebView(desktop = true)
        val before = pageLoads().size
        composeRule.runOnIdle { actions.setDesktopMode(false) }
        composeRule.waitUntil(TIMEOUT_MS) { pageLoads().size > before }
        val sent = pageLoads().last()
        assertTrue(sent, isMobile(sent))
    }

    @Test
    fun aMobileTab_switchedToDesktop_reloadsWithTheDesktopUserAgent() {
        setWebView(desktop = false)
        assertTrue(isMobile(pageLoads().first()))
        val before = pageLoads().size
        composeRule.runOnIdle { actions.setDesktopMode(true) }
        composeRule.waitUntil(TIMEOUT_MS) { pageLoads().size > before }
        assertFalse(isMobile(pageLoads().last()))
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val PAGE = "ua.html"
    }
}
