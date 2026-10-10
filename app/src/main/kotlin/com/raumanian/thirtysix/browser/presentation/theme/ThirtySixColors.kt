// Spec 019 — design-system tokens that have no Material 3 role (research R2).
// Read through `ThirtySixTheme.colors`, never as literals in feature code (Constitution §III).
@file:Suppress("MagicNumber")

package com.raumanian.thirtysix.browser.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended colour tokens. Light and dark instances below; [ThirtySixTheme] swaps the
 * hero glow and action gradient for wallpaper-derived values under dynamic color, and
 * keeps everything else fixed (FR-005, clarification Q2).
 */
@Immutable
data class ThirtySixColors(
    val fabStart: Color,
    val fabEnd: Color,
    val heroDeep: Color,
    val heroViolet: Color,
    val heroRose: Color,
    val glass: Color,
    val glassStroke: Color,
    val onGlass: Color,
    val onGlassMuted: Color,
    val surfaceIncognito: Color,
    val onIncognito: Color,
    val positive: Color,
    val tileSky: Color,
    val tileSkyInk: Color,
    val tileSun: Color,
    val tileSunInk: Color,
    val tileMint: Color,
    val tileMintInk: Color,
    val tilePeach: Color,
    val tilePeachInk: Color,
    /** Soft and bar shadows (shadow-soft / shadow-bar). */
    val shadow: Color,
    /** The new-tab action's glow (shadow-fab). */
    val glow: Color,
)

// INV-3 — the incognito plum is identical in both themes so incognito never reads as dark mode.
private val SurfaceIncognito = Color(0xFF17112B)
private val OnIncognito = Color(0xFFEDE7FE)
private val OnGlass = Color(0xFFFFFFFF)

internal val LightThirtySixColors = ThirtySixColors(
    fabStart = LightPrimary,
    fabEnd = Color(0xFF8F6FFF),
    heroDeep = Color(0xFF1D1442),
    heroViolet = LightPrimary,
    heroRose = Color(0xFFFF8FC7),
    glass = Color(0x26FFFFFF),
    glassStroke = Color(0x4DFFFFFF),
    onGlass = OnGlass,
    onGlassMuted = Color(0xCCFFFFFF),
    surfaceIncognito = SurfaceIncognito,
    onIncognito = OnIncognito,
    positive = Color(0xFF1D7A52),
    tileSky = Color(0xFFE5F2FF),
    tileSkyInk = Color(0xFF2A6FD6),
    tileSun = Color(0xFFFFF4D6),
    tileSunInk = Color(0xFFA26A00),
    tileMint = Color(0xFFE2F6EC),
    tileMintInk = Color(0xFF14845A),
    tilePeach = Color(0xFFFFE9E4),
    tilePeachInk = Color(0xFFD24A2C),
    shadow = Color(0x1A15141A),
    glow = Color(0x736C4CF1),
)

internal val DarkThirtySixColors = ThirtySixColors(
    fabStart = Color(0xFF8F72FF),
    fabEnd = Color(0xFFC8B8FF),
    heroDeep = Color(0xFF0B0820),
    heroViolet = Color(0xFF4A31C4),
    heroRose = Color(0xFF9B3F7E),
    glass = Color(0x1FFFFFFF),
    glassStroke = Color(0x33FFFFFF),
    onGlass = OnGlass,
    onGlassMuted = Color(0xB8FFFFFF),
    surfaceIncognito = SurfaceIncognito,
    onIncognito = OnIncognito,
    positive = Color(0xFF7BD8A5),
    tileSky = Color(0xFF1B3147),
    tileSkyInk = Color(0xFF9CCBFF),
    tileSun = Color(0xFF3D3214),
    tileSunInk = Color(0xFFF5CF6B),
    tileMint = Color(0xFF163628),
    tileMintInk = Color(0xFF86E0B0),
    tilePeach = Color(0xFF45231C),
    tilePeachInk = Color(0xFFFFAD97),
    shadow = Color(0x80000000),
    glow = Color(0x598F72FF),
)

val LocalThirtySixColors = staticCompositionLocalOf { LightThirtySixColors }
