package com.raumanian.thirtysix.browser.feature.browser.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** SC-004's ten cases. */
class TrackingParamStripperTest {
    private val stripper = TrackingParamStripper(ProtectionTestRules.params)

    @Test
    fun `mixed parameters with a fragment`() = check(
        "https://example.com/?a=1&utm_source=x&b=2&fbclid=y#top",
        "https://example.com/?a=1&b=2#top",
    )

    @Test
    fun `only tracking parameters leaves no question mark`() =
        check("https://a.example/?utm_source=x", "https://a.example/")

    @Test
    fun `prefix entries match every utm parameter`() = check(
        "https://a.example/p?utm_medium=m&utm_campaign=c&id=4",
        "https://a.example/p?id=4",
    )

    @Test
    fun `kept encoded values stay byte-identical`() = check(
        "https://a.example/s?q=caf%C3%A9+au+lait&gclid=1",
        "https://a.example/s?q=caf%C3%A9+au+lait",
    )

    @Test
    fun `repeated kept keys keep their order`() =
        check("https://a.example/?k=1&mc_eid=z&k=2", "https://a.example/?k=1&k=2")

    @Test
    fun `tracking names inside the fragment are untouched`() {
        val url = "https://a.example/page#utm_source=x"
        assertSame(url, stripper.strip(url))
    }

    @Test
    fun `generic names are kept`() {
        val url = "https://a.example/?ref=home&source=x&cid=3"
        assertSame(url, stripper.strip(url))
    }

    @Test
    fun `non-web addresses are untouched`() {
        val url = "mailto:a@b.example?subject=hi&utm_source=x"
        assertSame(url, stripper.strip(url))
    }

    @Test
    fun `empty value is still removed`() = check("http://a.example/x?fbclid=&b=", "http://a.example/x?b=")

    @Test
    fun `percent-encoded names are compared decoded`() =
        check("https://a.example/?%5Fga=1&x=2", "https://a.example/?x=2")

    private fun check(input: String, expected: String) = assertEquals(expected, stripper.strip(input))
}
