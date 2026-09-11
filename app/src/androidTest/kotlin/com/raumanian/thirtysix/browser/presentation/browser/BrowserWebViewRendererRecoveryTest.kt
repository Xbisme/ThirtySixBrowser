package com.raumanian.thirtysix.browser.presentation.browser

import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renderer-process recovery in [BrowserWebView].
 *
 * Until this was handled, losing the web engine's renderer process — a crash, or the system
 * reclaiming it for memory — ended the whole app, every tab with it. Lint's
 * `MissingOnRenderProcessGone`, which arrived with `androidx.webkit` in Spec 016, is what
 * pointed at it.
 *
 * `chrome://crash` and `chrome://kill` are the web engine's own debug addresses. Loaded
 * through the WebView API, they crash or kill the renderer behind the page, which are the two
 * ways the platform reports the loss. The host is a bare [BrowserWebView] with recording
 * callbacks, so each assertion is about the WebView's own recovery and nothing else.
 *
 * API 26+ only: that is when the platform began reporting renderer loss. Below it the
 * renderer runs inside the app's own process, so there is nothing left to recover.
 */
@RunWith(AndroidJUnit4::class)
class BrowserWebViewRendererRecoveryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val started = CopyOnWriteArrayList<String>()
    private val failures = CopyOnWriteArrayList<ErrorReason>()
    private val canGoBackChanges = CopyOnWriteArrayList<Boolean>()
    private val actions = WebViewActionsHandle()

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        composeRule.setContent {
            BrowserWebView(
                state = BrowserUiState(currentUrl = PAGE_URL, loadingState = LoadingState.Idle),
                homeUrl = PAGE_URL,
                actions = actions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = { started += it },
                    onProgressChanged = {},
                    onLoadFinished = {},
                    onLoadFailed = { failures += it },
                    onDownloadRequested = { _, _, _, _ -> },
                ),
                navigationCallbacks = BrowserNavigationCallbacks(
                    onUrlChange = {},
                    onCanGoBackChange = { canGoBackChanges += it },
                    onCanGoForwardChange = {},
                    onTitleChange = {},
                    onIconReceived = { _, _ -> },
                    onScreenshotReady = {},
                ),
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) { started.isNotEmpty() }
    }

    @Test
    fun rendererCrash_appSurvives_showsError_andReplacementWaitsForReload() {
        val firstWebViewReload = actions.reload

        composeRule.runOnUiThread { actions.loadUrl(CRASH_URL) }

        // A replacement WebView re-wires the handle, so a new reload lambda means it exists.
        composeRule.waitUntil(TIMEOUT_MS) { failures.isNotEmpty() && actions.reload !== firstWebViewReload }
        assertEquals(listOf<ErrorReason>(ErrorReason.Generic), failures.toList())
        assertEquals(false, canGoBackChanges.last())

        // A page that crashes its renderer must not be reloaded automatically, or it would crash
        // it again in a loop. The negative check needs a window of time to be meaningful.
        val startsAfterCrash = started.size
        SystemClock.sleep(NO_AUTO_RELOAD_WINDOW_MS)
        composeRule.waitForIdle()
        assertEquals(startsAfterCrash, started.size)

        // The replacement has loaded nothing, so Reload has to load the tab's address itself.
        composeRule.runOnUiThread { actions.reload() }
        composeRule.waitUntil(TIMEOUT_MS) { started.size > startsAfterCrash }
        assertEquals(PAGE_URL, started.last())
    }

    @Test
    fun rendererKilled_appSurvives_andReplacementReloadsThePageByItself() {
        val firstWebViewReload = actions.reload
        val startsBeforeKill = started.size

        composeRule.runOnUiThread { actions.loadUrl(KILL_URL) }

        composeRule.waitUntil(TIMEOUT_MS) {
            actions.reload !== firstWebViewReload && started.size > startsBeforeKill
        }
        assertEquals(PAGE_URL, started.last())
        assertTrue(failures.isEmpty())
    }

    private companion object {
        const val PAGE_URL = "about:blank"
        const val CRASH_URL = "chrome://crash"
        const val KILL_URL = "chrome://kill"
        const val TIMEOUT_MS = 10_000L
        const val NO_AUTO_RELOAD_WINDOW_MS = 2_000L
    }
}
