package com.raumanian.thirtysix.browser.presentation.entry

import com.raumanian.thirtysix.browser.domain.model.IncomingRequest

/**
 * Spec 020 research R3 — encodes an unconsumed [IncomingRequest] as two strings for the
 * activity's saved state, so a link that arrived during onboarding survives the system
 * reclaiming the app (FR-019). Pure Kotlin; the activity does the `Bundle` I/O.
 */
object PendingRequestState {
    private const val KIND_OPEN_ADDRESS = "open_address"
    private const val KIND_INTERPRET_TEXT = "interpret_text"
    private const val KIND_SEARCH_TEXT = "search_text"

    /** `kind` to `raw`. */
    fun encode(request: IncomingRequest): Pair<String, String> {
        val kind = when (request) {
            is IncomingRequest.OpenAddress -> KIND_OPEN_ADDRESS
            is IncomingRequest.InterpretText -> KIND_INTERPRET_TEXT
            is IncomingRequest.SearchText -> KIND_SEARCH_TEXT
        }
        return kind to request.raw
    }

    /** The request, or `null` when either part is missing or the kind is unknown. */
    fun decode(kind: String?, raw: String?): IncomingRequest? {
        if (raw == null) return null
        return when (kind) {
            KIND_OPEN_ADDRESS -> IncomingRequest.OpenAddress(raw)
            KIND_INTERPRET_TEXT -> IncomingRequest.InterpretText(raw)
            KIND_SEARCH_TEXT -> IncomingRequest.SearchText(raw)
            else -> null
        }
    }
}
