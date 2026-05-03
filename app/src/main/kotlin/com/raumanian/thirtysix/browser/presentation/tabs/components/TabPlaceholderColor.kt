package com.raumanian.thirtysix.browser.presentation.tabs.components

import kotlin.math.absoluteValue

/**
 * Spec 011 — deterministic placeholder style for tab cards (Q1 clarification:
 * text-only cards; no screenshot capture in v1.0).
 *
 * Pure-Kotlin (no Compose imports) so the unit test runs on JVM. The
 * Composable layer (`TabSwitcherCard`) maps [paletteRoleIndex] → an actual
 * `MaterialTheme.colorScheme.*` role at draw time per Spec 011 research R3
 * ("Tab thumbnail rendering — deterministic placeholder algorithm").
 *
 * @property paletteRoleIndex `0..7` — index into the 8-role palette resolved by
 *                            the Composable. Indices map to:
 *                            0=primary, 1=secondary, 2=tertiary, 3=surfaceVariant,
 *                            4=primaryContainer, 5=secondaryContainer, 6=tertiaryContainer,
 *                            7=inversePrimary. Error / errorContainer roles are
 *                            excluded to avoid implying a tab failure state
 *                            (analyze L2 remediation).
 * @property glyph First letter of the hostname (uppercased, non-alphanumeric
 *                 stripped). Falls back to `'?'` for empty / non-alphanumeric
 *                 hostnames.
 */
data class TabPlaceholderStyle(
    val paletteRoleIndex: Int,
    val glyph: Char,
)

object TabPlaceholderColor {
    /**
     * Number of roles in the M3 palette this placeholder algorithm rotates
     * through. See [TabPlaceholderStyle.paletteRoleIndex] KDoc for the index
     * → role mapping.
     */
    const val PALETTE_SIZE: Int = 8

    /**
     * Fallback glyph when [hostname] has no letter or digit characters
     * (e.g., empty string, pure punctuation, all-emoji).
     */
    const val FALLBACK_GLYPH: Char = '?'

    /**
     * Compute a deterministic [TabPlaceholderStyle] for [hostname]. Same input
     * always yields the same output across sessions and devices — visually
     * stable for users who recognize their tabs by color.
     */
    fun forHostname(hostname: String): TabPlaceholderStyle {
        val index = hostname.hashCode().absoluteValue % PALETTE_SIZE
        val glyph = hostname
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?: FALLBACK_GLYPH
        return TabPlaceholderStyle(paletteRoleIndex = index, glyph = glyph)
    }
}
