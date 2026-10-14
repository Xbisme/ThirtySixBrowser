package com.raumanian.thirtysix.browser.core.domain.model

// [Query.text] is trimmed but not yet URL-encoded; the search engine encodes it.
sealed interface AddressBarSubmitResult {
    data object Empty : AddressBarSubmitResult
    data class Url(val target: String) : AddressBarSubmitResult
    data class Query(val text: String) : AddressBarSubmitResult
}
