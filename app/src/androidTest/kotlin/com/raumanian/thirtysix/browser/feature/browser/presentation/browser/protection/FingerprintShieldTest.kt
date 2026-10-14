package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 024 T062, US5 — the basic fingerprinting shield on `fingerprint.html`, which reports its
 * read-outs as JSON in the title: same site → same values, another site → different values, the
 * top-level page decides inside a cross-site frame (FR-020), Fire's new secret changes them
 * (FR-003a), protection off gives the real values, battery is withheld and device ids are empty.
 * Skipped on web engines without page-start scripts (FR-022).
 */
@RunWith(AndroidJUnit4::class)
class FingerprintShieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val protection = TestProtection(withScripts = true, blockResolvableTracker = false)

    @Before
    fun requireFeature() = assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))

    @After
    fun tearDown() = server.close()

    private var started = false

    private fun read(host: String, page: String = PAGE): JSONObject {
        val url = server.url("fixtures024/$page", host)
        protection.title.set("")
        if (!started) {
            composeRule.setContent { protection.Content(url) }
            started = true
        } else {
            composeRule.runOnUiThread { protection.actions.loadUrl(url) }
        }
        val prefix = if (page == FRAMES) FRAME_PREFIX else "{"
        composeRule.waitUntil(TestProtection.TIMEOUT_MS) { protection.title.get().startsWith(prefix) }
        return JSONObject(protection.title.get().removePrefix(FRAME_PREFIX))
    }

    @Test
    fun readOutsAreStablePerSiteDifferBetweenSitesAndFollowTheTopPage() {
        val first = read(LOCAL)
        val again = read(LOCAL)
        val other = read(LOOPBACK)
        val framed = read(LOCAL, FRAMES)

        assertEquals(first.getString("c"), again.getString("c"))
        assertEquals(first.getString("i"), again.getString("i"))
        assertEquals(first.getString("a"), again.getString("a"))
        assertNotEquals("canvas same across sites", first.getString("c"), other.getString("c"))
        assertNotEquals("image data same across sites", first.getString("i"), other.getString("i"))
        assertEquals("the frame is served by the other host", LOOPBACK, framed.getString("host"))
        assertEquals("a frame follows its top-level page", first.getString("c"), framed.getString("c"))
        assertEquals("undefined", first.getString("b"))
        assertTrue("device ids exposed", first.getString("d").split('|').all { it.isEmpty() || it == "none" })
    }

    @Test
    fun aNewSecretChangesTheValues() {
        val before = read(LOCAL)

        composeRule.runOnUiThread { protection.scriptSession.regenerate() }
        composeRule.waitForIdle()
        val after = read(LOCAL)

        assertNotEquals(before.getString("c"), after.getString("c"))
    }

    @Test
    fun protectionOffGivesTheRealValues() {
        val protected = read(LOCAL)
        runBlocking { protection.exceptions.add(LOCAL) }
        composeRule.runOnUiThread { protection.hooks.reinstallScripts() }

        val off = read(LOCAL)
        val again = read(LOCAL)

        assertNotEquals(protected.getString("c"), off.getString("c"))
        assertEquals("the real value is stable", off.getString("c"), again.getString("c"))
        assertEquals("function", off.getString("b"))
    }

    private companion object {
        const val PAGE = "fingerprint.html"
        const val FRAMES = "frames.html"
        const val FRAME_PREFIX = "frame:"
        const val LOCAL = TestProtection.PAGE_HOST
        const val LOOPBACK = "127.0.0.1"
    }
}
