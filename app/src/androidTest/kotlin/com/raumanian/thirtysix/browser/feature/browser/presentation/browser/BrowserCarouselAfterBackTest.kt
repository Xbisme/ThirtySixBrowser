package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import com.raumanian.thirtysix.browser.feature.browser.fixture.BrowserScreenHolders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A page's own sideways scroller takes a drag after Back, and the drag does not turn into a swipe. */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserCarouselAfterBackTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb") },
            )
    }

    @get:org.junit.Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:org.junit.Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val server = AssetPageServer()

    @After
    fun tearDown() {
        server.close()
    }

    private fun findPage(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findPage(view.getChildAt(it)) }
        else -> null
    }

    private fun page(): WebView {
        var found: WebView? = null
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (found == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { found = findPage(composeRule.activity.window.decorView) }
            if (found == null) SystemClock.sleep(POLL_MS)
        }
        return requireNotNull(found) { "no page WebView" }
    }

    private fun evaluate(webView: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) {
                result = it
                done.countDown()
            }
        }
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return result
    }

    private fun awaitUrl(webView: WebView, url: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var current: String? = null
            instrumentation.runOnMainSync { current = webView.url }
            if (current == url && evaluate(webView, "document.readyState") == "\"complete\"") return
            SystemClock.sleep(POLL_MS)
        }
        var last: String? = null
        instrumentation.runOnMainSync { last = webView.url }
        error("did not reach $url, at $last, state ${evaluate(webView, "document.readyState")}")
    }

    /** The address changes as the load starts; the back entry exists only once it commits. */
    private fun awaitBackEntry(webView: WebView) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var canGoBack = false
        while (!canGoBack && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { canGoBack = webView.canGoBack() }
            if (!canGoBack) SystemClock.sleep(POLL_MS)
        }
        assertTrue("the second page never entered the history", canGoBack)
    }

    private fun dragInsideCarousel(webView: WebView) {
        val rect = evaluate(webView, "(r=>[r.left,r.top,r.width,r.height].join(','))(c.getBoundingClientRect())")
            .trim('"').split(',').map(String::toFloat)
        val origin = IntArray(2)
        var density = 1f
        instrumentation.runOnMainSync {
            webView.getLocationOnScreen(origin)
            density = webView.resources.displayMetrics.density
        }
        val y = origin[1] + (rect[1] + rect[3] / 2) * density
        val fromX = origin[0] + (rect[0] + rect[2] * FROM) * density
        val toX = origin[0] + (rect[0] + rect[2] * TO) * density
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, step: Int, x: Float) =
            instrumentation.sendPointerSync(MotionEvent.obtain(down, down + step * STEP_MS, action, x, y, 0))
        send(MotionEvent.ACTION_DOWN, 0, fromX)
        repeat(STEPS) { step ->
            val x = fromX + (toX - fromX) * (step + 1) / STEPS
            send(MotionEvent.ACTION_MOVE, step + 1, x)
        }
        send(MotionEvent.ACTION_UP, STEPS + 1, toX)
        SystemClock.sleep(SETTLE_MS)
    }

    @Test
    fun aCarouselStillScrollsAfterGoingBack() {
        hiltRule.inject()
        val holders = composeRule.runOnUiThread { BrowserScreenHolders(server.url(CAROUSEL)) }
        composeRule.runOnUiThread { composeRule.activity.setContent { holders.Route() } }
        val webView = page()
        awaitUrl(webView, server.url(CAROUSEL))
        SystemClock.sleep(PREVIEW_WAIT_MS)
        dragInsideCarousel(webView)

        instrumentation.runOnMainSync { webView.loadUrl(server.url(LANDING)) }
        awaitUrl(webView, server.url(LANDING))
        awaitBackEntry(webView)
        SystemClock.sleep(PREVIEW_WAIT_MS)
        instrumentation.runOnMainSync { webView.goBack() }
        awaitUrl(webView, server.url(CAROUSEL))
        // Past the moment the tab preview is taken after the page finishes.
        SystemClock.sleep(PREVIEW_WAIT_MS)

        dragInsideCarousel(webView)

        var url: String? = null
        instrumentation.runOnMainSync { url = webView.url }
        assertEquals("the drag must not navigate", server.url(CAROUSEL), url)
        val scrolled = evaluate(webView, "c.scrollLeft").toFloat()
        assertTrue("the carousel did not move: scrollLeft=$scrolled", scrolled > 0f)
    }

    private companion object {
        const val CAROUSEL = "carousel.html"
        const val LANDING = "landing.html"
        const val TIMEOUT_MS = 15_000L
        const val POLL_MS = 100L
        const val PREVIEW_WAIT_MS = 1_500L
        const val SETTLE_MS = 1_000L
        const val STEPS = 60
        const val STEP_MS = 16L
        const val FROM = 0.8f
        const val TO = 0.2f
    }
}
