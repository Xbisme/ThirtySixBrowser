package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

// Bundled protection data ships only inside the APK and is never fetched.
object AssetPaths {
    /** Tracker and ad-server hosts (EasyPrivacy + EasyList subset, CC BY-SA 3.0). */
    const val PROTECTION_TRACKER_HOSTS: String = "protection/tracker_hosts.txt"

    /** Public Suffix List (MPL-2.0), unchanged from upstream. */
    const val PROTECTION_PUBLIC_SUFFIXES: String = "protection/public_suffix_list.dat"

    /** Tracking query-parameter names; a trailing `*` marks a prefix. */
    const val PROTECTION_TRACKING_PARAMS: String = "protection/tracking_params.txt"

    /** Licence text for the tracker list. */
    const val LICENSE_CC_BY_SA_3: String = "licenses/CC-BY-SA-3.0.txt"

    /** Licence text for the Public Suffix List. */
    const val LICENSE_MPL_2: String = "licenses/MPL-2.0.txt"

    /** The fingerprinting shield (own code); installed first. */
    const val PRIVACY_FINGERPRINT_SCRIPT: String = "privacy/fingerprint.js"

    /** Cookie-banner auto-decline, built from autoconsent (MPL-2.0). */
    const val PRIVACY_CONSENT_SCRIPT: String = "privacy/consent.js"
}
