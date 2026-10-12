package com.raumanian.thirtysix.browser.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSuffixMatcherTest {
    private val matcher = ProtectionTestRules.matcher()

    @Test
    fun `plain domain and subdomain share a site`() {
        assertEquals("example.com", matcher.registrableDomain("example.com"))
        assertEquals("example.com", matcher.registrableDomain("a.b.example.com"))
    }

    @Test
    fun `multi-label suffix keeps one more label`() {
        assertEquals("example.co.uk", matcher.registrableDomain("www.example.co.uk"))
    }

    @Test
    fun `private section separates sites under the same suffix`() {
        assertEquals("alice.github.io", matcher.registrableDomain("alice.github.io"))
        assertTrue(matcher.isThirdParty("alice.github.io", "bob.github.io"))
    }

    @Test
    fun `wildcard rule makes every child a suffix`() {
        assertEquals("a.b.ck", matcher.registrableDomain("a.b.ck"))
        assertEquals("b.ck", matcher.registrableDomain("b.ck"))
    }

    @Test
    fun `exception rule beats the wildcard`() {
        assertEquals("www.ck", matcher.registrableDomain("www.ck"))
        assertEquals("www.ck", matcher.registrableDomain("x.www.ck"))
    }

    @Test
    fun `ip literals localhost and single labels are their own site`() {
        assertEquals("127.0.0.1", matcher.registrableDomain("127.0.0.1"))
        assertEquals("::1", matcher.registrableDomain("[::1]"))
        assertEquals("localhost", matcher.registrableDomain("localhost"))
        assertEquals("router", matcher.registrableDomain("router"))
        assertTrue(matcher.isThirdParty("127.0.0.1", "localhost"))
    }

    @Test
    fun `a host that is a public suffix is its own site`() {
        assertEquals("github.io", matcher.registrableDomain("github.io"))
        assertEquals("co.uk", matcher.registrableDomain("co.uk"))
    }

    @Test
    fun `unknown top-level label uses the implicit rule`() {
        assertEquals("site.zzz", matcher.registrableDomain("a.site.zzz"))
    }

    @Test
    fun `input is normalized`() {
        assertEquals("example.com", matcher.registrableDomain("WWW.Example.COM."))
        assertFalse(matcher.isThirdParty("CDN.example.com", "www.EXAMPLE.com"))
    }

    @Test
    fun `cache eviction keeps answers correct`() {
        val small = ProtectionTestRules.matcher(cacheSize = 2)
        repeat(3) { round ->
            listOf("a.example.com", "b.example.org", "c.co.uk", "d.github.io").forEach {
                small.registrableDomain(it)
            }
            assertEquals("round $round", "example.com", small.registrableDomain("x.example.com"))
        }
    }
}
