package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real touches on a real page: a sideways swipe the page cannot use goes back or forward. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SwipeNavigationTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val server = AssetPageServer()
    private val swipeState = SwipeNavigationState()
    private var enabled = true
    private var finished = CountDownLatch(1)
    private lateinit var page: BrowserPageView

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            page = BrowserPageView(composeRule.activity).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        finished.countDown()
                    }
                }
                swipe = SwipeNavigationHooks(swipeState, { enabled }, { false }, THRESHOLD_PX)
            }
            composeRule.activity.setContentView(page)
        }
        awaitLoad { page.loadUrl(server.url(PAGE_A)) }
        awaitLoad { page.loadUrl(server.url(PAGE_B)) }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun awaitLoad(action: () -> Unit) {
        finished = CountDownLatch(1)
        composeRule.activity.runOnUiThread(action)
        assertTrue("page did not finish", finished.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
    }

    private fun currentUrl(): String? {
        var url: String? = null
        instrumentation.runOnMainSync { url = page.url }
        return url
    }

    /** A slow drag across the middle of the page, from [fromFraction] to [toFraction] of its width. */
    private fun drag(fromFraction: Float, toFraction: Float, dyPx: Float = 0f) {
        val location = IntArray(2)
        instrumentation.runOnMainSync { page.getLocationOnScreen(location) }
        val y = location[1] + page.height / 2f
        val fromX = location[0] + page.width * fromFraction
        val toX = location[0] + page.width * toFraction
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, fromX, y, 0))
        repeat(STEPS) { step ->
            val fraction = (step + 1f) / STEPS
            val x = fromX + (toX - fromX) * fraction
            val time = down + (step + 1) * STEP_MS
            val move = MotionEvent.obtain(down, time, MotionEvent.ACTION_MOVE, x, y + dyPx * fraction, 0)
            instrumentation.sendPointerSync(move)
        }
        val up = down + (STEPS + 1) * STEP_MS
        instrumentation.sendPointerSync(MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, toX, y + dyPx, 0))
    }

    @Test
    fun swipingTowardTheEndGoesBackAndTowardTheStartGoesForward() {
        finished = CountDownLatch(1)
        drag(fromFraction = 0.2f, toFraction = 0.9f)
        assertTrue(finished.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
        assertEquals(server.url(PAGE_A), currentUrl())

        finished = CountDownLatch(1)
        drag(fromFraction = 0.9f, toFraction = 0.2f)
        assertTrue(finished.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
        assertEquals(server.url(PAGE_B), currentUrl())
    }

    @Test
    fun aShortSwipeDoesNothing() {
        drag(fromFraction = 0.4f, toFraction = 0.5f)
        instrumentation.waitForIdleSync()
        assertEquals(server.url(PAGE_B), currentUrl())
    }

    @Test
    fun aVerticalDragDoesNothing() {
        drag(fromFraction = 0.5f, toFraction = 0.55f, dyPx = VERTICAL_PX)
        instrumentation.waitForIdleSync()
        assertEquals(server.url(PAGE_B), currentUrl())
    }

    @Test
    fun nothingHappensWhileSwipingIsOff() {
        enabled = false
        drag(fromFraction = 0.2f, toFraction = 0.9f)
        instrumentation.waitForIdleSync()
        assertEquals(server.url(PAGE_B), currentUrl())
    }

    private companion object {
        const val PAGE_A = "landing.html"
        const val PAGE_B = "find.html"
        const val THRESHOLD_PX = 150f
        const val VERTICAL_PX = 600f
        const val STEPS = 20
        const val STEP_MS = 16L
        const val LOAD_TIMEOUT_S = 15L
    }
}
