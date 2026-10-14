package com.raumanian.thirtysix.browser.feature.settings.domain.model

/** Transient: never persisted, never logged with content. [failed] is always a subset of [requested]. */
data class ClearBrowsingDataResult(
    val requested: Set<ClearBrowsingDataCategory>,
    val failed: Set<ClearBrowsingDataCategory>,
) {
    init {
        require(requested.containsAll(failed)) { "failed must be a subset of requested" }
    }
}
