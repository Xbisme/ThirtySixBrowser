package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadReferrerTest {

    @Test
    fun `an incognito tab sends no referrer`() {
        assertNull(DownloadReferrer.of("https://a.com/page", "https://a.com/file.zip", isIncognito = true))
    }

    @Test
    fun `a page that is not http or https sends no referrer`() {
        assertNull(DownloadReferrer.of("data:text/html,hi", "https://a.com/file.zip", isIncognito = false))
        assertNull(DownloadReferrer.of(null, "https://a.com/file.zip", isIncognito = false))
        assertNull(DownloadReferrer.of("about:blank", "https://a.com/file.zip", isIncognito = false))
    }

    @Test
    fun `an https page downloading over http sends no referrer`() {
        assertNull(DownloadReferrer.of("https://a.com/page", "http://a.com/file.zip", isIncognito = false))
    }

    @Test
    fun `the same origin gets the full page address without its fragment`() {
        assertEquals(
            "https://a.com/docs/page?x=1",
            DownloadReferrer.of("https://a.com/docs/page?x=1#part", "https://a.com/file.zip", isIncognito = false),
        )
    }

    @Test
    fun `another origin gets only the page origin, port kept`() {
        assertEquals(
            "https://a.com:8443/",
            DownloadReferrer.of("https://a.com:8443/docs/page?x=1", "https://cdn.b.com/file.zip", isIncognito = false),
        )
        assertEquals(
            "https://a.com/",
            DownloadReferrer.of("https://a.com/page", "https://a.com:444/file.zip", isIncognito = false),
        )
    }
}
