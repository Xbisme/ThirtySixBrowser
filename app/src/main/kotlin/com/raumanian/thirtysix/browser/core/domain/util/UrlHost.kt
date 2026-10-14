package com.raumanian.thirtysix.browser.core.domain.util

import java.net.URI

/** The lower-case host of an `http`/`https` [url], or `null` for any other scheme or an unparsable address. */
fun urlHostOrNull(url: String): String? = runCatching { URI(url.trim()) }.getOrNull()
    ?.takeIf { uri -> uri.scheme?.lowercase().let { it == SCHEME_HTTP || it == SCHEME_HTTPS } }
    ?.host
    ?.trimEnd('.')
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
