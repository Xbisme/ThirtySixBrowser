package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageContext
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageRequest
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageRequestSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [BrowserChromeClient]'s location answer: the engine is never told to retain a decision, a request
 * with no page is refused, and an incognito request is marked so it is never remembered. No WebView
 * is built (one hung the next Compose test on API 29); the callback is invoked directly.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoPermissionDenialInstrumentedTest {

    private class CapturingSink(private val incognito: Boolean) : PageRequestSink {
        val requests = mutableListOf<PageRequest.Permission>()

        override fun context() = PageContext(tabId = -1L, generation = 1L, isIncognito = incognito)

        override fun onNavigationStarted() = Unit

        override fun enqueue(request: PageRequest) = request.cancel()

        override fun onPermissionRequest(request: PageRequest.Permission, hasUnsupported: Boolean) {
            requests += request
        }
    }

    private fun client(sink: PageRequestSink) = BrowserChromeClient({}, {}, { _, _ -> }, sink, IncognitoProfiles)

    @Test
    fun noPage_refusesWithoutRetaining() {
        var answer: Pair<Boolean, Boolean>? = null
        client(PageRequestSink.None).onGeolocationPermissionsShowPrompt(ORIGIN) { _, allow, retain ->
            answer = allow to retain
        }
        assertEquals(false to false, answer)
    }

    @Test
    fun incognitoRequest_isMarkedIncognito_andNeverRetained() {
        val sink = CapturingSink(incognito = true)
        var answer: Pair<Boolean, Boolean>? = null
        client(sink).onGeolocationPermissionsShowPrompt(ORIGIN) { _, allow, retain -> answer = allow to retain }

        val request = sink.requests.single()
        assertTrue(request.isIncognito)
        assertEquals(setOf(SiteCapability.Location), request.capabilities)
        assertEquals("https://incognito.example", request.origin)

        request.grant()
        assertEquals("allowed, but never retained by the engine", true to false, answer)
    }

    private companion object {
        const val ORIGIN = "https://incognito.example/"
    }
}
