package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.model.PublicSuffixRules
import com.raumanian.thirtysix.browser.core.domain.model.TrackerHostSet
import com.raumanian.thirtysix.browser.core.domain.model.TrackingParamRules
import java.net.IDN

/**
 * Spec 023 data-model "Rule data" — parsers for the three bundled assets. Each skips blank
 * lines and comments and ignores malformed entries rather than failing the whole list.
 */
object ProtectionAssetParser {
    private const val HASH_COMMENT = "#"
    private const val PSL_COMMENT = "//"
    private const val PSL_WILDCARD = "*."
    private const val PSL_EXCEPTION = "!"
    private const val PREFIX_MARK = "*"
    private val HOST = Regex("^[a-z0-9.-]+$")
    private val PARAM = Regex("^[A-Za-z0-9_.-]+$")

    fun parseTrackerHosts(lines: Sequence<String>): TrackerHostSet =
        TrackerHostSet(
            lines.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith(HASH_COMMENT) && HOST.matches(it) }
                .toHashSet(),
        )

    fun parsePublicSuffixes(lines: Sequence<String>): PublicSuffixRules {
        val normal = HashSet<String>()
        val wildcard = HashSet<String>()
        val exception = HashSet<String>()
        lines.forEach { raw ->
            // PSL rules end at the first whitespace; anything after is commentary.
            // Hosts reach us in ASCII (punycode); the list writes IDN rules in Unicode.
            val rule = toAscii(raw.trim().substringBefore(' ')).lowercase()
            when {
                rule.isEmpty() || rule.startsWith(PSL_COMMENT) -> Unit
                rule.startsWith(PSL_EXCEPTION) -> exception += rule.removePrefix(PSL_EXCEPTION)
                rule.startsWith(PSL_WILDCARD) -> wildcard += rule.removePrefix(PSL_WILDCARD)
                else -> normal += rule
            }
        }
        return PublicSuffixRules(normal, wildcard, exception)
    }

    private fun toAscii(rule: String): String =
        runCatching { IDN.toASCII(rule, IDN.ALLOW_UNASSIGNED) }.getOrDefault(rule)

    fun parseTrackingParams(lines: Sequence<String>): TrackingParamRules {
        val names = HashSet<String>()
        val prefixes = ArrayList<String>()
        lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(HASH_COMMENT) }
            .forEach { entry ->
                if (entry.endsWith(PREFIX_MARK)) {
                    entry.removeSuffix(PREFIX_MARK).takeIf { PARAM.matches(it) }?.let { prefixes += it }
                } else if (PARAM.matches(entry)) {
                    names += entry
                }
            }
        return TrackingParamRules(names, prefixes)
    }
}
