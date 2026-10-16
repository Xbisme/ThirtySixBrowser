package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tab's back and forward survives its WebView: saved through [TabSessionHooks] and restored into
 * a new WebView, as after a tab switch or a new process. The real process kill is checked on device.
 */
@RunWith(AndroidJUnit4::class)
class TabHistoryRestoreTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val server = AssetPageServer()
    private val webViews = mutableListOf<WebView>()
    private var finished = CountDownLatch(1)

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { webViews.forEach(WebView::destroy) }
        server.close()
    }

    private fun newWebView(): WebView {
        lateinit var created: WebView
        instrumentation.runOnMainSync {
            created = WebView(instrumentation.targetContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        finished.countDown()
                    }
                }
            }
            webViews += created
        }
        return created
    }

    private fun awaitLoad(action: () -> Unit) {
        finished = CountDownLatch(1)
        instrumentation.runOnMainSync(action)
        assertTrue("page did not finish", finished.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
    }

    private fun WebView.currentUrl(): String? {
        var current: String? = null
        instrumentation.runOnMainSync { current = url }
        return current
    }

    private fun saveFrom(webView: WebView): ByteArray {
        var saved: ByteArray? = null
        val hooks = TabSessionHooks(TAB_ID, { null }) { action -> saved = (action as TabSessionAction.Save).state }
        instrumentation.runOnMainSync { hooks.saveNow(webView) }
        return requireNotNull(saved) { "nothing was saved" }
    }

    private fun restoreInto(webView: WebView, state: ByteArray) {
        val hooks = TabSessionHooks(TAB_ID, { tabId -> state.takeIf { tabId == TAB_ID } }) {}
        var restored = false
        awaitLoad { restored = hooks.restore(webView) }
        assertTrue("restore failed", restored)
    }

    private fun visitThreePages(): WebView = newWebView().also { first ->
        listOf(PAGE_A, PAGE_B, PAGE_C).forEach { page -> awaitLoad { first.loadUrl(server.url(page)) } }
    }

    @Test
    fun aRestoredTabGoesBackThroughItsPages() {
        val state = saveFrom(visitThreePages())

        val second = newWebView()
        restoreInto(second, state)

        assertEquals(server.url(PAGE_C), second.currentUrl())
        awaitLoad { second.goBack() }
        assertEquals(server.url(PAGE_B), second.currentUrl())
        awaitLoad { second.goBack() }
        assertEquals(server.url(PAGE_A), second.currentUrl())
    }

    @Test
    fun aRestoredTabKeepsItsForwardPages() {
        val first = visitThreePages()
        awaitLoad { first.goBack() }
        val state = saveFrom(first)

        val second = newWebView()
        restoreInto(second, state)

        assertEquals(server.url(PAGE_B), second.currentUrl())
        var canGoForward = false
        instrumentation.runOnMainSync { canGoForward = second.canGoForward() }
        assertTrue(canGoForward)
        awaitLoad { second.goForward() }
        assertEquals(server.url(PAGE_C), second.currentUrl())
    }

    @Test
    fun aWebViewWithNoPagesSavesNothing() {
        val empty = newWebView()
        var saved: ByteArray? = null
        val hooks = TabSessionHooks(TAB_ID, { null }) { action -> saved = (action as TabSessionAction.Save).state }
        instrumentation.runOnMainSync { hooks.saveNow(empty) }
        assertEquals(null, saved)
    }

    @Test
    fun unreadableSavedStateIsNotRestored() {
        val webView = newWebView()
        val hooks = TabSessionHooks(TAB_ID, { byteArrayOf(1, 2, 3) }) {}
        var restored = true
        instrumentation.runOnMainSync { restored = hooks.restore(webView) }
        assertFalse(restored)
        assertNotNull(webView)
    }

    private companion object {
        const val TAB_ID = 7L
        const val PAGE_A = "landing.html"
        const val PAGE_B = "find.html"
        const val PAGE_C = "ua.html"
        const val LOAD_TIMEOUT_S = 15L
    }
}
