package com.raumanian.thirtysix.browser.feature.browser.domain.protection

import com.raumanian.thirtysix.browser.core.domain.model.PublicSuffixRules
import java.util.concurrent.ConcurrentHashMap

/**
 * The registrable domain ("site") of a host, from the bundled Public Suffix List. Thread-safe
 * and lock-free because WebView IO threads call it for every request; the cache is cleared
 * when it reaches [cacheSize].
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
        val labels = host.split('.')
        val isOwnSite = host.isEmpty() || isIpLiteral(host) || labels.size < 2 || labels.any { it.isEmpty() }
        // A host that is itself a public suffix (start 0) is its own site too.
        val suffixStart = if (isOwnSite) 0 else publicSuffixStart(labels)
        return if (suffixStart == 0) host else labels.subList(suffixStart - 1, labels.size).joinToString(".")
    }

    /** First label of the longest matching public suffix; with no match an unknown TLD is the suffix. */
    private fun publicSuffixStart(labels: List<String>): Int =
        labels.indices.firstNotNullOfOrNull { i -> suffixStartAt(labels, i) } ?: (labels.size - 1)

    private fun suffixStartAt(labels: List<String>, i: Int): Int? {
        val candidate = labels.subList(i, labels.size).joinToString(".")
        val parent = labels.subList(minOf(i + 1, labels.size), labels.size).joinToString(".")
        return when {
            candidate in rules.exception -> i + 1
            candidate in rules.normal -> i
            parent.isNotEmpty() && parent in rules.wildcard -> i
            else -> null
        }
    }

    private companion object {
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        fun normalize(host: String): String =
            host.trim().lowercase().removePrefix("[").removeSuffix("]").removeSuffix(".")

        fun isIpLiteral(host: String): Boolean = ':' in host || IPV4.matches(host)
    }
}
