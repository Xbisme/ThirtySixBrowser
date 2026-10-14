package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 022 research R2 — the desktop user agent, from default WebView user agents of API 24/29/36. */
class DesktopUserAgentTest {

    @Test
    fun `API 36 user agent becomes desktop Chrome on Linux with the same version`() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/133.0.6943.137 Safari/537.36",
            DesktopUserAgent.from(API_36),
        )
    }

    @Test
    fun `no recorded user agent keeps an Android, wv, Version or Mobile token`() {
        listOf(API_24, API_29, API_36).forEach { default ->
            val desktop = DesktopUserAgent.from(default)
            assertFalse(desktop, desktop.contains("Android"))
            assertFalse(desktop, desktop.contains("; wv"))
            assertFalse(desktop, desktop.contains("Version/"))
            assertFalse(desktop, desktop.contains("Mobile"))
            assertTrue(desktop, desktop.contains(DesktopUserAgent.PLATFORM_TOKEN))
        }
    }

    @Test
    fun `the Chrome version is kept`() {
        assertTrue(DesktopUserAgent.from(API_24).contains("Chrome/53.0.2785.135 "))
        assertTrue(DesktopUserAgent.from(API_29).contains("Chrome/74.0.3729.185 "))
    }

    private companion object {
        const val API_24 = "Mozilla/5.0 (Linux; Android 7.0; Android SDK built for x86 Build/NYC; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/53.0.2785.135 Mobile Safari/537.36"
        const val API_29 = "Mozilla/5.0 (Linux; Android 10; Android SDK built for x86_64 Build/QSR1.190920.001; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/74.0.3729.185 Mobile Safari/537.36"
        const val API_36 = "Mozilla/5.0 (Linux; Android 16; sdk_gphone16k_arm64 Build/BE2A.250530.026.F3; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/133.0.6943.137 Mobile Safari/537.36"
    }
}
