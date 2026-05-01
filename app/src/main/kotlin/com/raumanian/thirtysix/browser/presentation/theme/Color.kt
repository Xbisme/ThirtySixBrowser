// Spec 003 — Brand color palette generated 2026-05-01.
// Seeds: primary #0F766E (Deep Teal-700), tertiary #0891B2 (Cyan-600), secondary auto-derived (Slate).
// All values verified WCAG AA contrast for 24+ critical pairs (see specs/003-theme-typography-darkmode/research.md §4).
// PR review MUST cross-check against final Material Theme Builder export before merge.
//
// File-level @Suppress: hex color literals in this file ARE the constants per Constitution §III
// (Color tokens live in `presentation/theme/Color.kt`). Detekt's MagicNumber rule doesn't model
// domain semantics — every consumer references these via MaterialTheme.colorScheme.*, not hex.
@file:Suppress("MagicNumber")

package com.raumanian.thirtysix.browser.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// region — Light scheme (Deep Teal seed, Cyan tertiary)
private val MdThemeLightPrimary = Color(0xFF0F766E)
private val MdThemeLightOnPrimary = Color(0xFFFFFFFF)
private val MdThemeLightPrimaryContainer = Color(0xFFA7F3D0)
private val MdThemeLightOnPrimaryContainer = Color(0xFF002820)
private val MdThemeLightInversePrimary = Color(0xFF5EEAD4)

private val MdThemeLightSecondary = Color(0xFF475569)
private val MdThemeLightOnSecondary = Color(0xFFFFFFFF)
private val MdThemeLightSecondaryContainer = Color(0xFFCBD5E1)
private val MdThemeLightOnSecondaryContainer = Color(0xFF0F172A)

private val MdThemeLightTertiary = Color(0xFF0891B2)
private val MdThemeLightOnTertiary = Color(0xFFFFFFFF)
private val MdThemeLightTertiaryContainer = Color(0xFFCFFAFE)
private val MdThemeLightOnTertiaryContainer = Color(0xFF062B33)

private val MdThemeLightError = Color(0xFFB3261E)
private val MdThemeLightOnError = Color(0xFFFFFFFF)
private val MdThemeLightErrorContainer = Color(0xFFF9DEDC)
private val MdThemeLightOnErrorContainer = Color(0xFF410E0B)

private val MdThemeLightBackground = Color(0xFFFFFFFF)
private val MdThemeLightOnBackground = Color(0xFF1F2937)
private val MdThemeLightSurface = Color(0xFFFAFAF9)
private val MdThemeLightOnSurface = Color(0xFF1F2937)
private val MdThemeLightSurfaceVariant = Color(0xFFE5E7EB)
private val MdThemeLightOnSurfaceVariant = Color(0xFF374151)
private val MdThemeLightSurfaceTint = MdThemeLightPrimary
private val MdThemeLightInverseSurface = Color(0xFF1F2937)
private val MdThemeLightInverseOnSurface = Color(0xFFF9FAFB)

private val MdThemeLightOutline = Color(0xFF71717A)
private val MdThemeLightOutlineVariant = Color(0xFFD4D4D8)
private val MdThemeLightScrim = Color(0xFF000000)
// endregion

// region — Dark scheme (Deep Teal-300 primary, Cyan-300 tertiary)
private val MdThemeDarkPrimary = Color(0xFF5EEAD4)
private val MdThemeDarkOnPrimary = Color(0xFF003733)
private val MdThemeDarkPrimaryContainer = Color(0xFF0F5550)
private val MdThemeDarkOnPrimaryContainer = Color(0xFFA7F3D0)
private val MdThemeDarkInversePrimary = Color(0xFF0F766E)

private val MdThemeDarkSecondary = Color(0xFF94A3B8)
private val MdThemeDarkOnSecondary = Color(0xFF0F172A)
private val MdThemeDarkSecondaryContainer = Color(0xFF334155)
private val MdThemeDarkOnSecondaryContainer = Color(0xFFCBD5E1)

private val MdThemeDarkTertiary = Color(0xFF67E8F9)
private val MdThemeDarkOnTertiary = Color(0xFF06485A)
private val MdThemeDarkTertiaryContainer = Color(0xFF155E75)
private val MdThemeDarkOnTertiaryContainer = Color(0xFFCFFAFE)

private val MdThemeDarkError = Color(0xFFF2B8B5)
private val MdThemeDarkOnError = Color(0xFF601410)
private val MdThemeDarkErrorContainer = Color(0xFF8C1D18)
private val MdThemeDarkOnErrorContainer = Color(0xFFF9DEDC)

private val MdThemeDarkBackground = Color(0xFF0A0F0E)
private val MdThemeDarkOnBackground = Color(0xFFE5E7EB)
private val MdThemeDarkSurface = Color(0xFF0C1413)
private val MdThemeDarkOnSurface = Color(0xFFE5E7EB)
private val MdThemeDarkSurfaceVariant = Color(0xFF1F2937)
private val MdThemeDarkOnSurfaceVariant = Color(0xFF9CA3AF)
private val MdThemeDarkSurfaceTint = MdThemeDarkPrimary
private val MdThemeDarkInverseSurface = Color(0xFFE5E7EB)
private val MdThemeDarkInverseOnSurface = Color(0xFF1F2937)

private val MdThemeDarkOutline = Color(0xFF71717A)
private val MdThemeDarkOutlineVariant = Color(0xFF3F3F46)
private val MdThemeDarkScrim = Color(0xFF000000)
// endregion

internal val LightColorScheme = lightColorScheme(
    primary = MdThemeLightPrimary,
    onPrimary = MdThemeLightOnPrimary,
    primaryContainer = MdThemeLightPrimaryContainer,
    onPrimaryContainer = MdThemeLightOnPrimaryContainer,
    inversePrimary = MdThemeLightInversePrimary,
    secondary = MdThemeLightSecondary,
    onSecondary = MdThemeLightOnSecondary,
    secondaryContainer = MdThemeLightSecondaryContainer,
    onSecondaryContainer = MdThemeLightOnSecondaryContainer,
    tertiary = MdThemeLightTertiary,
    onTertiary = MdThemeLightOnTertiary,
    tertiaryContainer = MdThemeLightTertiaryContainer,
    onTertiaryContainer = MdThemeLightOnTertiaryContainer,
    error = MdThemeLightError,
    onError = MdThemeLightOnError,
    errorContainer = MdThemeLightErrorContainer,
    onErrorContainer = MdThemeLightOnErrorContainer,
    background = MdThemeLightBackground,
    onBackground = MdThemeLightOnBackground,
    surface = MdThemeLightSurface,
    onSurface = MdThemeLightOnSurface,
    surfaceVariant = MdThemeLightSurfaceVariant,
    onSurfaceVariant = MdThemeLightOnSurfaceVariant,
    surfaceTint = MdThemeLightSurfaceTint,
    inverseSurface = MdThemeLightInverseSurface,
    inverseOnSurface = MdThemeLightInverseOnSurface,
    outline = MdThemeLightOutline,
    outlineVariant = MdThemeLightOutlineVariant,
    scrim = MdThemeLightScrim,
)

internal val DarkColorScheme = darkColorScheme(
    primary = MdThemeDarkPrimary,
    onPrimary = MdThemeDarkOnPrimary,
    primaryContainer = MdThemeDarkPrimaryContainer,
    onPrimaryContainer = MdThemeDarkOnPrimaryContainer,
    inversePrimary = MdThemeDarkInversePrimary,
    secondary = MdThemeDarkSecondary,
    onSecondary = MdThemeDarkOnSecondary,
    secondaryContainer = MdThemeDarkSecondaryContainer,
    onSecondaryContainer = MdThemeDarkOnSecondaryContainer,
    tertiary = MdThemeDarkTertiary,
    onTertiary = MdThemeDarkOnTertiary,
    tertiaryContainer = MdThemeDarkTertiaryContainer,
    onTertiaryContainer = MdThemeDarkOnTertiaryContainer,
    error = MdThemeDarkError,
    onError = MdThemeDarkOnError,
    errorContainer = MdThemeDarkErrorContainer,
    onErrorContainer = MdThemeDarkOnErrorContainer,
    background = MdThemeDarkBackground,
    onBackground = MdThemeDarkOnBackground,
    surface = MdThemeDarkSurface,
    onSurface = MdThemeDarkOnSurface,
    surfaceVariant = MdThemeDarkSurfaceVariant,
    onSurfaceVariant = MdThemeDarkOnSurfaceVariant,
    surfaceTint = MdThemeDarkSurfaceTint,
    inverseSurface = MdThemeDarkInverseSurface,
    inverseOnSurface = MdThemeDarkInverseOnSurface,
    outline = MdThemeDarkOutline,
    outlineVariant = MdThemeDarkOutlineVariant,
    scrim = MdThemeDarkScrim,
)
