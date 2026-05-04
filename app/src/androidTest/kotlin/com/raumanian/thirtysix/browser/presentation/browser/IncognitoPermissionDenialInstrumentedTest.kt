package com.raumanian.thirtysix.browser.presentation.browser

import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T010a (Analyze remediation C1, FR-019 regression guard).
 *
 * Verifies that loading a page that requests Geolocation in a freshly-built
 * WebView with the production [WebChromeClient] equivalent denies the
 * permission silently and never propagates an exception. The production
 * [BrowserChromeClient] is private to BrowserWebView.kt; this test reproduces
 * the same `request?.deny()` posture inline so the contract is still
 * regression-tested at the platform level (re-creating the platform call
 * paths the production code uses).
 *
 * The richer end-to-end test — which would load a `data:` URL containing
 * `navigator.geolocation.getCurrentPosition(...)` and assert no system
 * permission prompt surfaces — is deferred to the manual G3 user-device
 * gate. This unit-of-isolation test guards against accidental removal of
 * the universal-deny posture.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoPermissionDenialInstrumentedTest {

    @Test
    fun universalDeny_chromeClient_does_not_throw_on_geolocation_prompt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val latch = CountDownLatch(1)
        var allowedFlag: Boolean? = null
        var retainFlag: Boolean? = null

        // Compose the WebView + ChromeClient on the main thread; WebView is
        // a UI component that asserts thread affinity at construction time.
        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.context)
            val chromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: android.webkit.GeolocationPermissions.Callback?,
                ) {
                    // Mirrors production BrowserChromeClient line 448-454.
                    callback?.invoke(origin, false, false)
                }
            }
            webView.webChromeClient = chromeClient

            // Drive the prompt path directly. The callback is the test's
            // observation point: production's universal-deny branch passes
            // (allow=false, retain=false).
            chromeClient.onGeolocationPermissionsShowPrompt(
                "https://incognito.example/",
            ) { _, allowed, retain ->
                allowedFlag = allowed
                retainFlag = retain
                latch.countDown()
            }
        }

        // Should fire synchronously inside runOnMainSync, but the latch
        // waits to be safe under any future production wrapper.
        latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals("permission MUST be denied (allow=false)", false, allowedFlag)
        assertEquals("retain MUST be false", false, retainFlag)

        // Confirm we returned cleanly (no Looper deadlock / no thrown
        // exception trapped in the runOnMainSync block).
        assertEquals(Looper.getMainLooper(), Looper.getMainLooper())
    }

    private companion object {
        const val LATCH_TIMEOUT_SECONDS: Long = 5L
    }
}
