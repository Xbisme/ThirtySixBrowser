package com.raumanian.thirtysix.browser.feature.browser.domain.protection

import com.raumanian.thirtysix.browser.core.domain.model.PublicSuffixRules
import com.raumanian.thirtysix.browser.core.domain.model.TrackerHostSet
import com.raumanian.thirtysix.browser.core.domain.model.TrackingParamRules
/** Small rule fixtures shared by the Spec 023 domain tests. */
internal object ProtectionTestRules {
    val suffixes = PublicSuffixRules(
        normal = setOf("com", "net", "org", "uk", "co.uk", "io", "github.io", "example", "ck", "test"),
        wildcard = setOf("ck"),
        exception = setOf("www.ck"),
    )

    fun matcher(cacheSize: Int = 64) = PublicSuffixMatcher(suffixes, cacheSize)

    val trackers = TrackerHostSet(setOf("tracker.example", "ads.net", "pixel.co.uk", "google.com"))

    val params = TrackingParamRules(
        names = setOf("fbclid", "gclid", "mc_eid", "_ga"),
        prefixes = listOf("utm_"),
    )
}
