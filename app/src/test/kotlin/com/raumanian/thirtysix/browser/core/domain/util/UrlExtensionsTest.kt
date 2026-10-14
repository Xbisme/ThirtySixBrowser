package com.raumanian.thirtysix.browser.core.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals(
            "not a url",
            "not a url".extractHostnameOrSelf(),
        )
    }

    @Test
    fun `user info and port are dropped`() {
        assertEquals("example.com", "https://user:pw@example.com:8443/".extractHostnameOrSelf())
    }

    @Test
    fun `bracketed IPv6 host keeps its brackets`() {
        assertEquals("[::1]", "http://[::1]/".extractHostnameOrSelf())
    }

    @Test
    fun `IPv6 host with port drops the port`() {
        assertEquals("[::1]", "http://[::1]:8080/".extractHostnameOrSelf())
    }

    @Test
    fun `backslash ends the authority`() {
        assertEquals("evil.com", "https://evil.com\\@good.com/".extractHostnameOrSelf())
    }

    @Test
    fun `query or fragment right after the host ends it`() {
        assertEquals("example.com", "https://example.com?q=1".extractHostnameOrSelf())
        assertEquals("example.com", "https://example.com#top".extractHostnameOrSelf())
    }

    @Test
    fun `percent-encoded host is decoded`() {
        assertEquals("exa mple.com", "https://exa%20mple.com/".extractHostnameOrSelf())
    }

    @Test
    fun `slash before colon means no scheme`() {
        assertEquals("a/b:c", "a/b:c".extractHostnameOrSelf())
    }

    @Test
    fun `scheme-relative address has a host`() {
        assertEquals("example.com", "//example.com/x".extractHostnameOrSelf())
    }

    // Expected values were read from android.net.Uri.getHost() under Robolectric.
    @Test
    fun `invalid escapes decode the way android net Uri does`() {
        assertEquals("bad\uFFFD\u0000z.com", "https://bad%zz.com/".extractHostnameOrSelf())
        assertEquals("a\uFFFD\u0002.com", "https://a%2z.com/".extractHostnameOrSelf())
        assertEquals("a\uFFFD", "https://a%2".extractHostnameOrSelf())
        assertEquals("\uFFFDx\uFFFD.com", "https://%C3x%A9.com/".extractHostnameOrSelf())
    }

    @Test
    fun `port is only a trailing run of digits`() {
        assertEquals("example.com:abc", "https://example.com:abc/".extractHostnameOrSelf())
    }
}
