package com.raumanian.thirtysix.browser.core.constants

/**
 * Paths of files bundled in `app/src/main/assets/` (Constitution §III). Spec 023 adds the
 * privacy-protection data, which ships only inside the APK and is never fetched (§I).
 */
object AssetPaths {
    /** Spec 023 research R1 — tracker and ad-server hosts (EasyPrivacy + EasyList subset, CC BY-SA 3.0). */
    const val PROTECTION_TRACKER_HOSTS: String = "protection/tracker_hosts.txt"

    /** Spec 023 research R3 — Public Suffix List (MPL-2.0), unchanged from upstream. */
    const val PROTECTION_PUBLIC_SUFFIXES: String = "protection/public_suffix_list.dat"

    /** Spec 023 research R8 — tracking query-parameter names; a trailing `*` marks a prefix. */
    const val PROTECTION_TRACKING_PARAMS: String = "protection/tracking_params.txt"

    /** Spec 023 FR-003 — licence text for the tracker list. */
    const val LICENSE_CC_BY_SA_3: String = "licenses/CC-BY-SA-3.0.txt"

    /** Spec 023 FR-003 — licence text for the Public Suffix List. */
    const val LICENSE_MPL_2: String = "licenses/MPL-2.0.txt"

    /** Spec 024 research R9 — the fingerprinting shield (own code); installed first. */
    const val PRIVACY_FINGERPRINT_SCRIPT: String = "privacy/fingerprint.js"

    /** Spec 024 research R7 — cookie-banner auto-decline, built from autoconsent (MPL-2.0). */
    const val PRIVACY_CONSENT_SCRIPT: String = "privacy/consent.js"
}
