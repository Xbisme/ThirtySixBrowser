package com.raumanian.thirtysix.browser.data.local.protection

import com.raumanian.thirtysix.browser.core.constants.AssetPaths
import com.raumanian.thirtysix.browser.domain.protection.TrackerMatcher
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 023 T024 — the three bundled-asset parsers, plus a check of the real committed assets. */
class ProtectionAssetParserTest {

    @Test
    fun `tracker hosts skip comments blanks and malformed lines`() {
        val set = ProtectionAssetParser.parseTrackerHosts(
            sequenceOf("# header", "", "  Tracker.Example ", "bad host!", "ads.net"),
        )
        assertEquals(2, set.size)
        assertTrue(set.containsHostOrParent("tracker.example", stopAt = "tracker.example"))
    }

    @Test
    fun `public suffix rules are split by kind and IDN rules become ASCII`() {
        val rules = ProtectionAssetParser.parsePublicSuffixes(
            sequenceOf("// comment", "com", "*.ck", "!www.ck", "co.uk  trailing", "公司.cn", ""),
        )
        assertEquals(setOf("com", "co.uk", "xn--55qx5d.cn"), rules.normal)
        assertEquals(setOf("ck"), rules.wildcard)
        assertEquals(setOf("www.ck"), rules.exception)
    }

    @Test
    fun `tracking params split names and prefixes`() {
        val rules = ProtectionAssetParser.parseTrackingParams(sequenceOf("# c", "utm_*", "fbclid", "", "bad param"))
        assertTrue(rules.matches("utm_source"))
        assertTrue(rules.matches("fbclid"))
        assertFalse(rules.matches("ref"))
        assertFalse(rules.matches("bad param"))
    }

    @Test
    fun `committed assets parse and respect the never-block set`() {
        val trackers = ProtectionAssetParser.parseTrackerHosts(asset(AssetPaths.PROTECTION_TRACKER_HOSTS))
        val suffixes = ProtectionAssetParser.parsePublicSuffixes(asset(AssetPaths.PROTECTION_PUBLIC_SUFFIXES))
        val params = ProtectionAssetParser.parseTrackingParams(asset(AssetPaths.PROTECTION_TRACKING_PARAMS))

        assertTrue("tracker list is empty", trackers.size > MIN_TRACKERS)
        assertTrue("suffix list is empty", suffixes.size > MIN_SUFFIX_RULES)
        assertTrue(params.matches("utm_campaign"))
        assertFalse("generic names must stay out", params.matches("ref"))
        TrackerMatcher.NEVER_BLOCK_HOSTS.forEach { host ->
            assertFalse("$host must never be listed", trackers.containsHostOrParent(host, stopAt = host))
        }
        assertTrue(trackers.containsHostOrParent("google-analytics.com", stopAt = "google-analytics.com"))
        assertTrue(trackers.containsHostOrParent("doubleclick.net", stopAt = "doubleclick.net"))
    }

    private fun asset(path: String): Sequence<String> =
        File(ASSETS_DIR, path).readLines().asSequence()

    private companion object {
        // Unit tests run with the module directory as working directory.
        const val ASSETS_DIR = "src/main/assets"
        const val MIN_TRACKERS = 5_000
        const val MIN_SUFFIX_RULES = 5_000
    }
}
