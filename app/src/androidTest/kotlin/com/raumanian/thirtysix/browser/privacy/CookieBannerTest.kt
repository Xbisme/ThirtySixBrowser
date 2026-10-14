package com.raumanian.thirtysix.browser.privacy

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.protection.TestProtection
import com.raumanian.thirtysix.browser.testing.AssetPageServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 024 T060, FR-024 — the bundled cookie-banner script refuses on every covered consent tool,
 * never accepts, and stands down when the site's protection is off. Each fixture records the button
 * clicked in its title (`choice:<value>`). Skipped on web engines without page-start scripts
 * (FR-022), such as CI's API 29 emulator.
 */
@RunWith(AndroidJUnit4::class)
class CookieBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val protection = TestProtection(withScripts = true)

    @Before
    fun requireFeature() = assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))

    @After
    fun tearDown() = server.close()

    private fun open(tool: String) {
        val url = server.url("fixtures024/consent/$tool.html?reset", TestProtection.PAGE_HOST)
        composeRule.setContent { protection.Content(url) }
        composeRule.waitUntil(TestProtection.TIMEOUT_MS) { protection.loadsFinished.get() > 0 }
    }

    @Test
    fun everyCoveredConsentToolIsRefusedAndNeverAccepted() {
        val results = mutableMapOf<String, String>()
        open(TOOLS.first())
        TOOLS.forEach { tool ->
            val url = server.url("fixtures024/consent/$tool.html?reset", TestProtection.PAGE_HOST)
            composeRule.runOnUiThread { protection.actions.loadUrl(url) }
            composeRule.waitUntil(BANNER_TIMEOUT_MS) { protection.title.get().startsWith(CHOICE) }
            results[tool] = protection.title.get()
        }

        TOOLS.forEach { tool ->
            val recorded = results.getValue(tool)
            assertFalse("$tool: an accept option was chosen ($recorded)", recorded.contains("accept"))
            assertEquals("$tool: banner not refused", "reject", recorded.removePrefix(CHOICE).substringAfterLast('>'))
        }
    }

    @Test
    fun aSiteWithProtectionOffKeepsItsBanner() {
        runBlocking { protection.exceptions.add(TestProtection.PAGE_HOST) }
        open("onetrust")
        composeRule.runOnUiThread { protection.hooks.reinstallScripts() }
        composeRule.runOnUiThread { protection.actions.reload() }

        Thread.sleep(UNTOUCHED_WAIT_MS)

        assertFalse("the banner was handled", protection.title.get().startsWith(CHOICE))
    }

    private companion object {
        const val CHOICE = "choice:"
        const val BANNER_TIMEOUT_MS = 15_000L
        const val UNTOUCHED_WAIT_MS = 5_000L
        val TOOLS = listOf(
            "onetrust", "cookiebot", "didomi", "quantcast", "usercentrics",
            "trustarc", "complianz", "cookieyes", "iubenda",
        )
    }
}
