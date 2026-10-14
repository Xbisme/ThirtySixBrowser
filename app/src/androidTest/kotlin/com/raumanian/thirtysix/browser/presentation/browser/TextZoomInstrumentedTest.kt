package com.raumanian.thirtysix.browser.presentation.browser

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 022 T045 / FR-031 — a new text size reaches the live WebView without reloading the page,
 * and a WebView built afterwards starts with it.
 */
@RunWith(AndroidJUnit4::class)
class TextZoomInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val loadsStarted = AtomicInteger()
    private val loadsFinished = AtomicInteger()
    private var setZoom: (Int) -> Unit = {}

    @After
    fun tearDown() = server.close()

    private fun setWebView(initialZoom: Int) {
        val url = server.url("find.html")
        composeRule.setContent {
            var zoom by remember { mutableIntStateOf(initialZoom) }
            setZoom = { zoom = it }
            BrowserWebView(
                incognitoProfile = IncognitoProfiles,
                state = remember { BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle) },
                actions = remember { WebViewActionsHandle() },
                callbacks = BrowserWebViewCallbacks(
                    onLoadStarted = { loadsStarted.incrementAndGet() },
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
                textZoomPercent = zoom,
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) { loadsFinished.get() > 0 }
    }

    private fun webViewTextZoom(): Int {
        var value = -1
        onView(isAssignableFrom(WebView::class.java)).check { view, _ -> value = (view as WebView).settings.textZoom }
        return value
    }

    @Test
    fun aNewWebViewStartsWithTheChosenSize() {
        setWebView(initialZoom = ZOOM_150)
        assertEquals(ZOOM_150, webViewTextZoom())
    }

    @Test
    fun changingTheSize_updatesTheLiveWebView_withoutAReload() {
        setWebView(initialZoom = ZOOM_100)
        val startedBefore = loadsStarted.get()
        composeRule.runOnIdle { setZoom(ZOOM_175) }
        composeRule.waitForIdle()
        assertEquals(ZOOM_175, webViewTextZoom())
        assertEquals("no page load is started by a size change", startedBefore, loadsStarted.get())
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val ZOOM_100 = 100
        const val ZOOM_150 = 150
        const val ZOOM_175 = 175
    }
}
