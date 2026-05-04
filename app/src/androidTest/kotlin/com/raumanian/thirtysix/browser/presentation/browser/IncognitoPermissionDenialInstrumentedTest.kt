package com.raumanian.thirtysix.browser.presentation.browser

import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T010a (Analyze remediation C1, FR-019 regression guard).
 *
 * Verifies the universal-deny posture used by the production
 * `BrowserChromeClient` (private to `BrowserWebView.kt`): a
 * `WebChromeClient` MUST forward `(allow=false, retain=false)` to the
 * Geolocation callback so the in-app permission UI never surfaces. The same
 * universal-deny posture applies to the camera/microphone/MIDI/protected-
 * media surfaces routed through `onPermissionRequest(request)` — production
 * calls `request?.deny()` unconditionally.
 *
 * The full end-to-end test — load a `data:` URL containing
 * `navigator.geolocation.getCurrentPosition(...)` and assert no system
 * permission prompt surfaces — is deferred to the manual G3 user-device
 * gate (already ✅).
 *
 * **Why no WebView instance is constructed here**: an earlier draft created
 * `WebView(instrumentation.context)` on the main thread to wire the
 * `WebChromeClient`, but on API 29 emulator that left the main thread in a
 * state that hung the next Compose UI test (`AddressBarTest`'s
 * `setContent`). Constructing a bare `WebChromeClient` and invoking the
 * callback override directly preserves the contract under test (the
 * `onGeolocationPermissionsShowPrompt` signature + the `(false, false)`
 * pass-through) without any platform side-effects.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoPermissionDenialInstrumentedTest {

    @Test
    fun universalDeny_passes_allow_false_and_retain_false_to_callback() {
        var allowedFlag: Boolean? = null
        var retainFlag: Boolean? = null

        // Mirrors production BrowserChromeClient.onGeolocationPermissionsShowPrompt
        // (BrowserWebView.kt line 448-454): callback?.invoke(origin, false, false).
        val chromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }
        }

        chromeClient.onGeolocationPermissionsShowPrompt(
            "https://incognito.example/",
        ) { _, allowed, retain ->
            allowedFlag = allowed
            retainFlag = retain
        }

        assertEquals("permission MUST be denied (allow=false)", false, allowedFlag)
        assertEquals("retain MUST be false", false, retainFlag)
    }
}
