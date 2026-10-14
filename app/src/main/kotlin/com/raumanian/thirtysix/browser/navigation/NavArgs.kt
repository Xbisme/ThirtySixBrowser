package com.raumanian.thirtysix.browser.navigation

/** Bundle and navigation argument keys — single source of truth (Constitution §III). */
object NavArgs {
    /** Spec 020 research R3 — kind of an unconsumed incoming request saved before process death. */
    const val PENDING_REQUEST_KIND: String = "pending_incoming_request_kind"

    /** Spec 020 research R3 — raw text of that request. */
    const val PENDING_REQUEST_RAW: String = "pending_incoming_request_raw"
}
