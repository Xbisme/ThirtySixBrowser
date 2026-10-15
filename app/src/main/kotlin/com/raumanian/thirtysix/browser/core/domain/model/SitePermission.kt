package com.raumanian.thirtysix.browser.core.domain.model

/** What a site can ask the browser for; everything else is refused. */
enum class SiteCapability(val storageValue: String) {
    Camera("camera"),
    Microphone("microphone"),
    Location("location"),
    ;

    companion object {
        fun fromStorage(value: String): SiteCapability? = entries.firstOrNull { it.storageValue == value }
    }
}

/** A remembered answer. "Allow this time" is never remembered. */
enum class SiteDecision(val storageValue: String) {
    Allow("allow"),
    Deny("deny"),
    ;

    companion object {
        fun fromStorage(value: String): SiteDecision? = entries.firstOrNull { it.storageValue == value }
    }
}

data class SitePermissionDecision(
    val origin: String,
    val capability: SiteCapability,
    val decision: SiteDecision,
    val decidedAt: Long,
)
