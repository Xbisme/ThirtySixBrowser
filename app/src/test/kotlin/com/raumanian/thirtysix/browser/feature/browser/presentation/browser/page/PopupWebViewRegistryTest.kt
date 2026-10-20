package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.app.Application
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A pop-up whose tab never composes must not stay alive in the registry. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PopupWebViewRegistryTest {

    private val registry = PopupWebViewRegistry()

    private fun waitingPopup(tabId: Long): Pair<WebView, String> {
        val webView = WebView(ApplicationProvider.getApplicationContext())
        val address = registry.hold(webView, OPENER_TAB_ID)
        registry.onTabCreated(webView, tabId)
        return webView to address
    }

    @Test
    fun `forgetting a tab destroys and removes the pop-up waiting for it only`() {
        val (closed, closedAddress) = waitingPopup(tabId = CLOSED_TAB_ID)
        val (kept, keptAddress) = waitingPopup(tabId = OTHER_TAB_ID)

        registry.forgetTab(CLOSED_TAB_ID)

        assertTrue(shadowOf(closed).wasDestroyCalled())
        assertNull(registry.take(closedAddress))
        assertFalse(shadowOf(kept).wasDestroyCalled())
        assertTrue(registry.take(keptAddress) === kept)
    }

    @Test
    fun `clearing destroys and removes every waiting pop-up`() {
        val (webView, address) = waitingPopup(tabId = CLOSED_TAB_ID)

        registry.clear()

        assertTrue(shadowOf(webView).wasDestroyCalled())
        assertNull(registry.take(address))
    }

    private companion object {
        const val OPENER_TAB_ID = 1L
        const val CLOSED_TAB_ID = 2L
        const val OTHER_TAB_ID = 3L
    }
}
