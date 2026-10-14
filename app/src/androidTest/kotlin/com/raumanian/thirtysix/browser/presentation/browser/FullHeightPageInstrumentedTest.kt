package com.raumanian.thirtysix.browser.presentation.browser

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.testing.AssetPageServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A page sized with `html, body { height: 100% }` fills the WebView. Without MATCH_PARENT layout
 * params the WebView was laid out as WRAP_CONTENT, so such pages (maps, drawing apps) got a body
 * 0 px tall and rendered blank.
 */
@RunWith(AndroidJUnit4::class)
class FullHeightPageInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val loadsFinished = AtomicInteger()

    @After
    fun tearDown() = server.close()

    @Test
    fun aFullHeightPage_fillsTheWebView() {
        val url = server.url("fullheight.html")
        composeRule.setContent {
            BrowserWebView(
                state = remember { BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle) },
                actions = remember { WebViewActionsHandle() },
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
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) { loadsFinished.get() > 0 }

        var webView: WebView? = null
        onView(isAssignableFrom(WebView::class.java)).check { view, _ -> webView = view as WebView }
        val view = requireNotNull(webView)
        val bodyHeight = AtomicReference<String>()
        val done = CountDownLatch(1)
        composeRule.runOnUiThread {
            view.evaluateJavascript(BODY_HEIGHT_SCRIPT) { value ->
                bodyHeight.set(value)
                done.countDown()
            }
        }
        assertTrue("script did not answer", done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        val cssHeight = bodyHeight.get().trim('"').toDouble()
        val viewCssHeight = view.height / view.resources.displayMetrics.density
        assertTrue(
            "body is $cssHeight CSS px tall in a WebView $viewCssHeight CSS px tall",
            cssHeight >= viewCssHeight * MIN_FILL_RATIO,
        )
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val BODY_HEIGHT_SCRIPT = "String(document.body.getBoundingClientRect().height)"

        // Text zoom and rounding may shift the figure a little; a blank page reports 0.
        const val MIN_FILL_RATIO = 0.9
    }
}
