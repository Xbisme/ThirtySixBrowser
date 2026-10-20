package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renderer-process recovery in a bare [BrowserWebView]. `chrome://crash` and `chrome://kill` crash
 * or kill the renderer, the two ways the platform reports its loss. API 26+ only: below that the
 * renderer runs inside the app's process.
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
                incognitoProfile = IncognitoProfiles,
                state = BrowserUiState(currentUrl = PAGE_URL, loadingState = LoadingState.Idle),
                actions = actions,
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = { started += it },
                    onProgressChanged = {},
                    onLoadFinished = {},
                    onLoadFailed = { failures += it },
                    onDownloadRequested = {},
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
