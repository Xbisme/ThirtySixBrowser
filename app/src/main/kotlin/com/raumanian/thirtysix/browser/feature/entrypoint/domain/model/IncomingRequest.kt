package com.raumanian.thirtysix.browser.feature.entrypoint.domain.model

/** Never persisted beyond the activity's saved state, never logged. */
sealed interface IncomingRequest {
    val raw: String

    data class OpenAddress(override val raw: String) : IncomingRequest

    /** Share-sheet text: opens the first address in it, or searches it. */
    data class InterpretText(override val raw: String) : IncomingRequest

    /** Always searched, even when it looks like an address. */
    data class SearchText(override val raw: String) : IncomingRequest
}

sealed interface IncomingTarget {
    data class Load(val url: String) : IncomingTarget

    data class Search(val text: String) : IncomingTarget

    /** Unsafe or malformed address: never loaded; the person is told. */
    data object Rejected : IncomingTarget

    /** Empty after trimming: open the app and do nothing else. */
    data object Nothing : IncomingTarget
}

sealed interface OpenOutcome {
    data class OpenedInNewTab(val tabId: Long) : OpenOutcome

    // When loadInPlace, the caller loads url into the live page so the replaced page stays one Back
    // away; otherwise the tab's stored address was already set and the tab-switch load opens it.
    data class OpenedInExistingTab(val tabId: Long, val url: String, val loadInPlace: Boolean) : OpenOutcome

    data object Duplicate : OpenOutcome

    data object Failed : OpenOutcome
}
