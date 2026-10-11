package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 020 data-model §1 — what another app handed to ThirtySix, once the presentation layer
 * has read the `Intent`. Pure Kotlin: the decisions about it are made in the domain layer.
 *
 * Never persisted beyond the activity's saved state, never logged (FR-016).
 */
sealed interface IncomingRequest {
    /** The raw string, exactly as delivered. */
    val raw: String

    /** A web address another app asked to open (`ACTION_VIEW`). */
    data class OpenAddress(override val raw: String) : IncomingRequest

    /** Text from the Share sheet: open the first address in it, or search it (FR-010). */
    data class InterpretText(override val raw: String) : IncomingRequest

    /** Text the person asked to search — selection menu or system web search (FR-011, FR-012). */
    data class SearchText(override val raw: String) : IncomingRequest
}

/** Spec 020 data-model §2 — what an [IncomingRequest] resolves to. */
sealed interface IncomingTarget {
    /** A validated http/https address to open. */
    data class Load(val url: String) : IncomingTarget

    /** Trimmed text, at most 2,048 characters, to search with the chosen engine. */
    data class Search(val text: String) : IncomingTarget

    /** Unsafe or malformed address: never loaded, the person is told (FR-015). */
    data object Rejected : IncomingTarget

    /** Empty after trimming: open the app and do nothing else (FR-013). */
    data object Nothing : IncomingTarget
}

/** Spec 020 data-model §3 — what opening an [IncomingTarget] did to the tabs. */
sealed interface OpenOutcome {
    /** A new normal tab was created and is active. */
    data class OpenedInNewTab(val tabId: Long) : OpenOutcome

    /**
     * The tab cap was reached: the most recently used normal tab was reused (FR-018).
     *
     * [loadInPlace] is `true` when that tab was already the active one: the caller must load
     * [url] into the live page, so the replaced page stays one Back away. When `false`, the
     * tab's stored address was set to [url] before it was switched to, so the browser's normal
     * tab-switch load opens it (research R6).
     */
    data class OpenedInExistingTab(val tabId: Long, val url: String, val loadInPlace: Boolean) : OpenOutcome

    /** The same address was opened moments ago: nothing done (FR-007). */
    data object Duplicate : OpenOutcome

    /** The tab store failed for a reason other than the cap. */
    data object Failed : OpenOutcome
}
