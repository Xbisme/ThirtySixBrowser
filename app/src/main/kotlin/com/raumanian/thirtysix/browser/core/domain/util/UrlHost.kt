package com.raumanian.thirtysix.browser.core.domain.util

import java.net.URI

/**
 * Spec 024 data-model step 1 — the lower-case host of an `http`/`https` [url], or `null` for any
 * other scheme or an address that does not parse. Pure Kotlin, so domain code can use it.
 */
fun urlHostOrNull(url: String): String? = runCatching { URI(url.trim()) }.getOrNull()
    ?.takeIf { uri -> uri.scheme?.lowercase().let { it == SCHEME_HTTP || it == SCHEME_HTTPS } }
    ?.host
    ?.trimEnd('.')
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
