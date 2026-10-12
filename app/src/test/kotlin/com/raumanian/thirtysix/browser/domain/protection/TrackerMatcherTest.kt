package com.raumanian.thirtysix.browser.domain.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerMatcherTest {
    private val matcher = TrackerMatcher(ProtectionTestRules.trackers, ProtectionTestRules.matcher())

    @Test
    fun `listed third-party host is blocked`() {
        assertTrue(matcher.shouldBlock("tracker.example", "news.com", protectionOn = true))
    }

    @Test
    fun `subdomain of a listed host is blocked`() {
        assertTrue(matcher.shouldBlock("stats.tracker.example", "news.com", protectionOn = true))
        assertTrue(matcher.shouldBlock("a.pixel.co.uk", "news.com", protectionOn = true))
    }

    @Test
    fun `first-party requests are never blocked`() {
        assertFalse(matcher.shouldBlock("cdn.tracker.example", "www.tracker.example", protectionOn = true))
    }

    @Test
    fun `never-block hosts win over the list`() {
        assertFalse(matcher.shouldBlock("accounts.google.com", "news.com", protectionOn = true))
        assertTrue(matcher.shouldBlock("google.com", "news.com", protectionOn = true))
    }

    @Test
    fun `nothing is blocked with protection off`() {
        assertFalse(matcher.shouldBlock("tracker.example", "news.com", protectionOn = false))
    }

    @Test
    fun `unlisted host and a listed public suffix parent are not blocked`() {
        assertFalse(matcher.shouldBlock("cdn.news.org", "news.com", protectionOn = true))
        // "co.uk" is a suffix; only pixel.co.uk is listed.
        assertFalse(matcher.shouldBlock("other.co.uk", "news.com", protectionOn = true))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(matcher.shouldBlock("Stats.TRACKER.example", "News.com", protectionOn = true))
    }
}
