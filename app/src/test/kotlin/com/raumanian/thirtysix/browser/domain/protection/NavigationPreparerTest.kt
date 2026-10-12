package com.raumanian.thirtysix.browser.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationPreparerTest {
    private val normalOff = mutableSetOf<String>()
    private val incognitoOff = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    private val preparer = NavigationPreparer(
        stripper = TrackingParamStripper(ProtectionTestRules.params),
        suffixes = ProtectionTestRules.matcher(),
        isSiteProtected = { site, incognito -> site !in (if (incognito) incognitoOff else normalOff) },
        failedHosts = { failed },
    )

    @Test
    fun `strips then upgrades`() {
        val prepared = preparer.prepare("http://shop.example.com/i?id=4&utm_source=m", isIncognito = false)
        assertEquals("https://shop.example.com/i?id=4", prepared.url)
        assertEquals("http://shop.example.com/i?id=4", prepared.upgradedFrom)
    }

    @Test
    fun `excepted site is left untouched`() {
        normalOff += "example.com"
        val url = "http://www.example.com/?utm_source=m"
        assertEquals(url, preparer.prepare(url, isIncognito = false).url)
    }

    @Test
    fun `normal and incognito exceptions are separate`() {
        incognitoOff += "example.com"
        val url = "http://www.example.com/?utm_source=m"
        assertEquals(url, preparer.prepare(url, isIncognito = true).url)
        assertEquals("https://www.example.com/", preparer.prepare(url, isIncognito = false).url)
    }

    @Test
    fun `failed host is only stripped`() {
        failed += "old.example.org"
        val prepared = preparer.prepare("http://old.example.org/?gclid=1", isIncognito = false)
        assertEquals("http://old.example.org/", prepared.url)
        assertNull(prepared.upgradedFrom)
    }

    @Test
    fun `prepare is idempotent`() {
        listOf(
            "http://shop.example.com/i?id=4&utm_source=m#x",
            "https://a.example/?fbclid=1",
            "http://localhost:8023/?utm_source=x",
            "about:blank",
        ).forEach { url ->
            val once = preparer.prepare(url, isIncognito = false).url
            assertEquals(url, once, preparer.prepare(once, isIncognito = false).url)
        }
    }
}
