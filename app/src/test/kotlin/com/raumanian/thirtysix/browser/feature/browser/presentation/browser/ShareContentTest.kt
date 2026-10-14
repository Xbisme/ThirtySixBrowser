package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 022 FR-023 — the address is the text; the title is the subject only when present. */
class ShareContentTest {

    @Test
    fun `the address is shared with the title as subject`() {
        val content = ShareContent.of(URL, "Example Domain")
        assertEquals(URL, content.text)
        assertEquals("Example Domain", content.subject)
    }

    @Test
    fun `an empty or blank title gives no subject`() {
        assertNull(ShareContent.of(URL, "").subject)
        assertNull(ShareContent.of(URL, "   ").subject)
        assertNull(ShareContent.of(URL, null).subject)
    }

    private companion object {
        const val URL = "https://example.com/a?b=1"
    }
}
