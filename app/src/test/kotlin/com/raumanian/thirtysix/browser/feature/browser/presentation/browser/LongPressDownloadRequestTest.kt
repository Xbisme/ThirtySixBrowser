package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LongPressDownloadRequestTest {

    @Test
    fun `a long-press download carries no cookies and a minimal referrer`() {
        val request = longPressDownloadRequest(
            url = "https://cdn.b.com/photo.jpg",
            userAgent = "UA",
            pageUrl = "https://a.com/gallery?id=1",
            isIncognito = false,
        )

        assertNull(request.cookies)
        assertEquals("https://a.com/", request.referer)
        assertEquals("https://cdn.b.com/photo.jpg", request.url)
    }

    @Test
    fun `a same-site long-press link still sends only the origin, since it may redirect elsewhere`() {
        val request = longPressDownloadRequest(
            url = "https://a.com/redirect?to=https://b.com/x",
            userAgent = "UA",
            pageUrl = "https://a.com/gallery?id=1",
            isIncognito = false,
        )

        assertEquals("https://a.com/", request.referer)
    }
}
