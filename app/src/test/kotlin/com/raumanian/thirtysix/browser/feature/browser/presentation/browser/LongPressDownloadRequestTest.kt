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
}
