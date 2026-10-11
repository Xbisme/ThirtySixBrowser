package com.raumanian.thirtysix.browser.domain.model

/** Spec 021 — what a site can ask the browser for (FR-001). Everything else is refused (FR-008). */
enum class SiteCapability(val storageValue: String) {
    Camera("camera"),
    Microphone("microphone"),
    Location("location"),
    ;

    companion object {
        fun fromStorage(value: String): SiteCapability? = entries.firstOrNull { it.storageValue == value }
    }
}

/** Spec 021 — a remembered answer. "Allow this time" is never remembered (FR-003). */
enum class SiteDecision(val storageValue: String) {
    Allow("allow"),
    Deny("deny"),
    ;

    companion object {
        fun fromStorage(value: String): SiteDecision? = entries.firstOrNull { it.storageValue == value }
    }
}

/** Spec 021 data-model §1 — one remembered decision for one site and one capability. */
data class SitePermissionDecision(
    val origin: String,
    val capability: SiteCapability,
    val decision: SiteDecision,
    val decidedAt: Long,
)
