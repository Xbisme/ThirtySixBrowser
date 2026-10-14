package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the server receives: local addresses are not upgraded, tracking parameters never arrive,
 * the GPC header does, and cross-site referrers stop at the origin.
 */
@RunWith(AndroidJUnit4::class)
class NavigationProtectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val protection = TestProtection()

    @After
    fun tearDown() = server.close()

    private fun open(path: String, until: () -> Boolean) {
        composeRule.setContent { protection.Content(server.url(path, TestProtection.PAGE_HOST)) }
        composeRule.waitUntil(TestProtection.TIMEOUT_MS, until)
    }

    @Test
    fun aLocalHttpAddressIsNotUpgradedAndIsMarkedNotSecure() {
        open("headers.html") { server.headersFor("headers.html").isNotEmpty() && protection.loadsFinished.get() > 0 }

        val status = protection.monitor.get()!!.status.value
        assertTrue(status.isWebPage)
        assertFalse(status.connectionSecure)
    }

    @Test
    fun trackingParametersNeverReachTheServer() {
        open("params.html?a=1&utm_source=mail&b=2&gclid=z") { server.headersFor("params.html").isNotEmpty() }

        assertEquals("/params.html?a=1&b=2", server.paths().first { it.startsWith("/params.html") })
    }

    @Test
    fun aLinkTheTappedPageOpensIsCleanedBeforeTheRequest() {
        open("params.html?go=1") { server.headersFor("landing.html").isNotEmpty() }

        assertEquals("/landing.html?x=1", server.paths().first { it.startsWith("/landing.html") })
        // The app re-issued this navigation itself, so it carries no referrer.
        assertNull(server.headersFor("landing.html").first()["referer"])
    }

    @Test
    fun theGpcSignalReachesThePageAndWhereSupportedItsResources() {
        open("headers.html") { server.headersFor("resource.gif").isNotEmpty() }

        assertEquals(UrlConstants.GPC_HEADER_VALUE, server.headersFor("headers.html").first()["sec-gpc"])
        assumeTrue("profile headers unsupported on this WebView", GpcHeaderInstaller.appWide)
        assertEquals(UrlConstants.GPC_HEADER_VALUE, server.headersFor("resource.gif").first()["sec-gpc"])
    }

    @Test
    fun crossSiteRequestsSendAtMostTheOrigin() {
        // 127.0.0.1 is on the test tracker list; with protection off the image loads, and the
        // referrer stays the engine's default either way.
        runBlocking { protection.exceptions.add(TestProtection.PAGE_HOST) }
        open("referrer.html?secret=1") {
            server.headersFor("cross.gif").isNotEmpty() && server.headersFor("same.gif").isNotEmpty()
        }

        val origin = "http://${TestProtection.PAGE_HOST}:${server.port}/"
        assertEquals("${origin}referrer.html?secret=1", server.headersFor("same.gif").first()["referer"])
        // Engines before Chromium 85 default to sending the full address across sites, which the
        // app cannot change for page resources (CI's API 29 has WebView 74).
        assumeTrue("WebView older than 85", webViewMajorVersion() >= ORIGIN_ONLY_DEFAULT_SINCE)
        assertEquals(origin, server.headersFor("cross.gif").first()["referer"])
    }

    private fun webViewMajorVersion(): Int {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val version = WebViewCompat.getCurrentWebViewPackage(context)?.versionName.orEmpty()
        return version.substringBefore('.').toIntOrNull() ?: 0
    }

    private companion object {
        /** Chromium made `strict-origin-when-cross-origin` the default referrer policy in M85. */
        const val ORIGIN_ONLY_DEFAULT_SINCE = 85
    }
}
