package com.raumanian.thirtysix.browser.core.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 024 data-model step 1 — hosts of recorded addresses. */
class UrlHostTest {
    @Test
    fun `http and https hosts are returned lower-case`() {
        assertEquals("mail.example.com", urlHostOrNull("https://Mail.Example.com/inbox?x=1"))
        assertEquals("127.0.0.1", urlHostOrNull("http://127.0.0.1:8024/fixtures024/storage.html"))
        assertEquals("example.com", urlHostOrNull("https://example.com./"))
    }

    @Test
    fun `other schemes and unparseable addresses give null`() {
        assertNull(urlHostOrNull("thirtysix:home"))
        assertNull(urlHostOrNull("about:blank"))
        assertNull(urlHostOrNull("file:///sdcard/a.html"))
        assertNull(urlHostOrNull("https://exa mple.com/"))
        assertNull(urlHostOrNull(""))
    }
}
