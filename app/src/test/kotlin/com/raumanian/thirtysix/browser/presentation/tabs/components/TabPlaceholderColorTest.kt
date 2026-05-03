package com.raumanian.thirtysix.browser.presentation.tabs.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 011 (T023) — pure-JVM unit tests for [TabPlaceholderColor]'s
 * deterministic placeholder algorithm (R3).
 */
class TabPlaceholderColorTest {

    @Test
    fun `same hostname always yields same TabPlaceholderStyle across calls`() {
        val a = TabPlaceholderColor.forHostname("example.com")
        val b = TabPlaceholderColor.forHostname("example.com")
        assertEquals(a, b)
    }

    @Test
    fun `paletteRoleIndex is always within 0 until PALETTE_SIZE`() {
        val hostnames = listOf(
            "example.com",
            "duckduckgo.com",
            "www.google.com",
            "news.ycombinator.com",
            "github.com",
            "wikipedia.org",
            "developer.android.com",
            "kotlinlang.org",
            "youtube.com",
            "stackoverflow.com",
        )
        hostnames.forEach { host ->
            val style = TabPlaceholderColor.forHostname(host)
            assertTrue(
                "paletteRoleIndex out of range for $host: ${style.paletteRoleIndex}",
                style.paletteRoleIndex in 0 until TabPlaceholderColor.PALETTE_SIZE,
            )
        }
    }

    @Test
    fun `glyph derivation strips non-alphanumeric prefix`() {
        // Leading "www." → first letter-or-digit is 'w'/'W'.
        assertEquals('W', TabPlaceholderColor.forHostname("www.example.com").glyph)
        // Pure punctuation prefix.
        assertEquals('A', TabPlaceholderColor.forHostname("...about.com").glyph)
    }

    @Test
    fun `empty or non-alphanumeric hostname falls back to question mark glyph`() {
        assertEquals(TabPlaceholderColor.FALLBACK_GLYPH, TabPlaceholderColor.forHostname("").glyph)
        assertEquals(TabPlaceholderColor.FALLBACK_GLYPH, TabPlaceholderColor.forHostname("...").glyph)
        // Pure-emoji hostname (rare in practice but the fallback should kick in).
        assertEquals(TabPlaceholderColor.FALLBACK_GLYPH, TabPlaceholderColor.forHostname("🍕").glyph)
    }
}
