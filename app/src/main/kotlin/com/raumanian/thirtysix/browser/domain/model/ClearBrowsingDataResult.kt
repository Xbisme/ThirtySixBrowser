package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 016 FR-033 — what one clear achieved (data-model §4). Transient: never persisted, never
 * logged with content.
 *
 * @property requested what the user selected; never empty, because the dialog disables confirm
 *   while nothing is selected (FR-026).
 * @property failed the categories whose work threw or reported failure. Always a subset of
 *   [requested].
 */
data class ClearBrowsingDataResult(
    val requested: Set<ClearBrowsingDataCategory>,
    val failed: Set<ClearBrowsingDataCategory>,
) {
    init {
        require(requested.containsAll(failed)) { "failed must be a subset of requested" }
    }
}
