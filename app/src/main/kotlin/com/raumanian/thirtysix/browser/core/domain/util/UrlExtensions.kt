package com.raumanian.thirtysix.browser.core.domain.util

import java.io.ByteArrayOutputStream

// `www.` is kept: it can be a meaningful subdomain, and the raw host copies faithfully.
internal fun String.extractHostnameOrSelf(): String {
    if (isBlank()) return this
    val host = hostOf(this)
    return if (host.isNullOrBlank()) this else host
}

// Same split as android.net.Uri.getHost(), kept free of Android so domain code can use it.
private fun hostOf(uri: String): String? {
    val schemeEnd = uri.indexOfFirst { it == ':' || it in "/?#" }.takeIf { it >= 0 && uri[it] == ':' } ?: -1
    val start = schemeEnd + 1
    if (!uri.startsWith("//", start)) return null
    val authorityEnd = uri.indexOfFirst(start + 2) { it in "/\\?#" }
    val authority = uri.substring(start + 2, authorityEnd)
    val hostStart = authority.lastIndexOf('@') + 1
    // A port is a trailing run of ASCII digits after the last ':'.
    val port = authority.trimEnd { it in '0'..'9' }.let { if (it.endsWith(':')) it.length - 1 else authority.length }
    return percentDecode(authority.substring(hostStart, port))
}

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return length
}

// Mirrors Android's UriCodec, quirks included: a bad hex digit adds U+FFFD and still keeps the
// partial byte; an escape cut off by the end adds U+FFFD and stops.
private fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val out = StringBuilder()
    val bytes = ByteArrayOutputStream()
    fun flush() {
        if (bytes.size() > 0) out.append(bytes.toString(Charsets.UTF_8.name())).also { bytes.reset() }
    }
    var i = 0
    var truncated = false
    while (i < text.length && !truncated) {
        val c = text[i++]
        if (c != '%') {
            flush()
            out.append(c)
            continue
        }
        var value = 0
        var digits = 0
        while (digits < 2) {
            val next = text.getOrNull(i++)?.digitToIntOrNull(HEX)
            if (next == null) {
                flush()
                out.append(REPLACEMENT)
                truncated = i > text.length
                break
            }
            value = value * HEX + next
            digits++
        }
        if (!truncated) bytes.write(value)
    }
    flush()
    return out.toString()
}

private const val HEX = 16
private const val REPLACEMENT = '\uFFFD'
