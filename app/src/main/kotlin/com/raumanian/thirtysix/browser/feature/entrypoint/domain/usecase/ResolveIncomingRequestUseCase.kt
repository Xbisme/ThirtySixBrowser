package com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.UrlPatterns
import com.raumanian.thirtysix.browser.domain.model.AddressBarSubmitResult
import com.raumanian.thirtysix.browser.domain.model.classifyAddressBarInput
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingTarget
import java.net.URI
import javax.inject.Inject

private val embeddedWebUrlRegex = Regex(UrlPatterns.EMBEDDED_WEB_URL_REGEX, RegexOption.IGNORE_CASE)
private val webSchemes = setOf("http", "https")
private const val TRAILING_PUNCTUATION = ".,;:!?)]}'\""

/**
 * Spec 020 research R4 / contracts C2 — decides what content from another app means.
 *
 *  - `OpenAddress`: only an http/https address with an authority loads; anything else —
 *    another scheme, no host, unparseable, longer than 2,048 characters — is refused (FR-015).
 *    It is refused rather than truncated: a shortened address would load a different page.
 *  - `InterpretText`: a lone token the address bar would treat as an address opens it; else
 *    the first explicit `http(s)://` link in the text opens; else the text is searched (FR-010).
 *  - `SearchText`: always searched, even when it looks like an address (FR-011).
 *
 * Text is trimmed and cut to 2,048 characters before use (FR-013). Pure Kotlin.
 */
class ResolveIncomingRequestUseCase @Inject constructor() {

    operator fun invoke(request: IncomingRequest): IncomingTarget = when (request) {
        is IncomingRequest.OpenAddress -> resolveAddress(request.raw)
        is IncomingRequest.InterpretText -> interpret(request.raw)
        is IncomingRequest.SearchText -> search(request.raw)
    }

    private fun resolveAddress(raw: String): IncomingTarget {
        val address = raw.trim()
        if (address.length > BrowserLimits.MAX_INCOMING_TEXT_LENGTH) return IncomingTarget.Rejected
        return validWebAddress(address)?.let(IncomingTarget::Load) ?: IncomingTarget.Rejected
    }

    private fun interpret(raw: String): IncomingTarget {
        val text = raw.trim().take(BrowserLimits.MAX_INCOMING_TEXT_LENGTH).trim()
        if (text.isEmpty()) return IncomingTarget.Nothing
        val address = loneAddress(text) ?: embeddedAddress(text)
        return address?.let(IncomingTarget::Load) ?: IncomingTarget.Search(text)
    }

    private fun search(raw: String): IncomingTarget {
        val text = raw.trim().take(BrowserLimits.MAX_INCOMING_TEXT_LENGTH).trim()
        return if (text.isEmpty()) IncomingTarget.Nothing else IncomingTarget.Search(text)
    }

    /** The address a single token stands for, using the address bar's own rule (research R4). */
    private fun loneAddress(text: String): String? =
        text.takeUnless { it.any(Char::isWhitespace) }
            ?.let { classifyAddressBarInput(it) as? AddressBarSubmitResult.Url }
            ?.let { validWebAddress(it.target) }

    private fun embeddedAddress(text: String): String? =
        embeddedWebUrlRegex.find(text)
            ?.value
            ?.trimEnd { it in TRAILING_PUNCTUATION }
            ?.let(::validWebAddress)

    /** [candidate] when it parses as an http/https address with an authority, else `null`. */
    private fun validWebAddress(candidate: String): String? =
        runCatching { URI(candidate) }.getOrNull()
            ?.takeIf { uri -> uri.scheme?.lowercase() in webSchemes && !uri.rawAuthority.isNullOrBlank() }
            ?.let { candidate }
}
