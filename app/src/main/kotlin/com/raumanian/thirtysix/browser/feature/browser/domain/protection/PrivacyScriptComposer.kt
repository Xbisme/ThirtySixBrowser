package com.raumanian.thirtysix.browser.feature.browser.domain.protection

/**
 * Spec 024 research R5, contracts C18 — the text installed as the page-start script.
 *
 * Each bundled script is a function expression `(function(cfg){ … })`. The composer calls each one
 * with the same frozen settings object `{v, s, x}`, built here from **local data only**
 * (Constitution §II-1): `s` is the session secret, `x` the registrable domains with protection off.
 * Nothing is assigned to `window`, so a page cannot read the settings.
 *
 * Inputs are validated rather than trusted: the secret must be lower-case hex, and a site that is not
 * a plain host name (letters, digits, `-`, `.`) is dropped. Pure Kotlin.
 */
object PrivacyScriptComposer {
    private const val VERSION = 1
    private val HEX = Regex("^[0-9a-f]+$")
    private val HOST = Regex("^[a-z0-9.-]+$")

    fun compose(secret: String, exceptedSites: Set<String>, bundles: List<String>): String {
        require(HEX.matches(secret)) { "The session secret must be lower-case hex" }
        val sites = exceptedSites.map { it.lowercase() }.filter { HOST.matches(it) }.sorted()
        val prelude = buildString {
            append("{\"v\":").append(VERSION)
            append(",\"s\":").append(jsonString(secret))
            append(",\"x\":[")
            sites.forEachIndexed { index, site ->
                if (index > 0) append(',')
                append(jsonString(site))
            }
            append("]}")
        }
        return bundles.joinToString(separator = "\n") { bundle ->
            "(${bundle.trim()})(Object.freeze($prelude));"
        }
    }

    /** A JSON string literal that is also safe inside an HTML `<script>` and a JS source. */
    internal fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
