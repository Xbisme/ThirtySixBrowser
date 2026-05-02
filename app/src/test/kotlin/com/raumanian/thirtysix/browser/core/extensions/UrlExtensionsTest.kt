package com.raumanian.thirtysix.browser.core.extensions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spec 009 (T039) — pure JVM (Robolectric) unit tests for
 * [extractHostnameOrSelf]. Robolectric provides the `android.net.Uri`
 * shadow needed for `String.toUri()`. Project-level Robolectric pinning
 * (SDK 33) lives in [robolectric.properties] (Spec 005).
 *
 * Coverage covers research R4 + R8 cases:
 *  - host present (typical https URL)
 *  - host with subdomain (no www-stripping per R8 policy)
 *  - host absent (`about:blank`, `file:///`)
 *  - empty / whitespace input
 *  - IPv4 host with port
 */
@RunWith(RobolectricTestRunner::class)
class UrlExtensionsTest {

    @Test
    fun `https URL returns hostname only`() {
        assertEquals(
            "developer.android.com",
            "https://developer.android.com/jetpack/compose?utm=test".extractHostnameOrSelf(),
        )
    }

    @Test
    fun `host with www subdomain preserves www (no stripping per R8)`() {
        assertEquals(
            "www.example.com",
            "https://www.example.com".extractHostnameOrSelf(),
        )
    }

    @Test
    fun `aboutBlank has no host so falls back to full URL`() {
        assertEquals("about:blank", "about:blank".extractHostnameOrSelf())
    }

    @Test
    fun `file URL has no host so falls back to full URL`() {
        assertEquals(
            "file:///sdcard/page.html",
            "file:///sdcard/page.html".extractHostnameOrSelf(),
        )
    }

    @Test
    fun `empty string returns empty string`() {
        assertEquals("", "".extractHostnameOrSelf())
    }

    @Test
    fun `blank string returns same blank string`() {
        assertEquals("   ", "   ".extractHostnameOrSelf())
    }

    @Test
    fun `IPv4 host with port returns just the host`() {
        assertEquals(
            "192.168.1.1",
            "https://192.168.1.1:8080/path".extractHostnameOrSelf(),
        )
    }

    @Test
    fun `malformed input without scheme is returned as-is`() {
        // No scheme + no `://` → Uri.parse can't extract host. Falls back.
        assertEquals(
            "not a url",
            "not a url".extractHostnameOrSelf(),
        )
    }
}
