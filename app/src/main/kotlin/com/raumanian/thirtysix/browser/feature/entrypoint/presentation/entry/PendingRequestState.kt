package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
/** Saved-state encoding of an unconsumed request, so a link that arrived during onboarding survives process death. */
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
