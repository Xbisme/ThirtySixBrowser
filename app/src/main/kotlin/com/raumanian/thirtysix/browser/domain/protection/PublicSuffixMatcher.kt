package com.raumanian.thirtysix.browser.domain.protection

import com.raumanian.thirtysix.browser.domain.model.PublicSuffixRules
import java.util.concurrent.ConcurrentHashMap

/**
 * Spec 023 data-model rule 1 — the registrable domain ("site") of a host, from the bundled
 * Public Suffix List. Two hosts are the same site when their registrable domains are equal
 * (FR-008).
 *
 * Thread-safe and lock-free: it is called from WebView IO threads for every request. Results
 * are cached; when the cache reaches [cacheSize] it is simply cleared.
 */
class PublicSuffixMatcher(
    private val rules: PublicSuffixRules,
    private val cacheSize: Int,
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun registrableDomain(host: String): String {
        val normalized = normalize(host)
        cache[normalized]?.let { return it }
        val result = compute(normalized)
        if (cache.size >= cacheSize) cache.clear()
        cache[normalized] = result
        return result
    }

    fun isThirdParty(requestHost: String, topHost: String): Boolean =
        registrableDomain(requestHost) != registrableDomain(topHost)

    private fun compute(host: String): String {
        if (host.isEmpty() || isIpLiteral(host) || '.' !in host) return host
        val labels = host.split('.')
        if (labels.any { it.isEmpty() }) return host
        val suffixStart = publicSuffixStart(labels)
        // The host is itself a public suffix: it is its own site.
        if (suffixStart == 0) return host
        return labels.subList(suffixStart - 1, labels.size).joinToString(".")
    }

    /** Index of the first label of the longest matching public suffix. */
    private fun publicSuffixStart(labels: List<String>): Int {
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in rules.exception) return i + 1
            if (candidate in rules.normal) return i
            if (i + 1 < labels.size) {
                val parent = labels.subList(i + 1, labels.size).joinToString(".")
                if (parent in rules.wildcard) return i
            }
        }
        // Implicit "*" rule: an unknown top-level label is the public suffix.
        return labels.size - 1
    }

    private companion object {
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        fun normalize(host: String): String =
            host.trim().lowercase().removePrefix("[").removeSuffix("]").removeSuffix(".")

        fun isIpLiteral(host: String): Boolean = ':' in host || IPV4.matches(host)
    }
}
