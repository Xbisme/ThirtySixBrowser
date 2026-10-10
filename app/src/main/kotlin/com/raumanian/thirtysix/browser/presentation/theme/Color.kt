// Spec 019 — violet brand palette from the approved ThirtySix design system
// (https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys, snapshot in specs/019-ui-redesign/assets/tokens.json).
// Replaces Spec 003's Deep Teal palette. Token → Material 3 role mapping: research R2.
// Every text/surface pair here is ≥ 4.5:1 in both themes (checked by ThirtySixColorsTest).
//
// File-level @Suppress: hex color literals in this file ARE the constants per Constitution §III
// (Color tokens live in `presentation/theme/`). Detekt's MagicNumber rule doesn't model domain
// semantics — every consumer references these via MaterialTheme.colorScheme.* or ThirtySixTheme.colors.
@file:Suppress("MagicNumber")

package com.raumanian.thirtysix.browser.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// region — Light scheme
internal val LightPrimary = Color(0xFF6C4CF1) // primary
internal val LightOnPrimary = Color(0xFFFFFFFF) // on-primary
private val LightPrimaryContainer = Color(0xFFF0ECFF) // primary-soft
private val LightOnPrimaryContainer = Color(0xFF3D2A99) // on-primary-soft
private val LightInversePrimary = Color(0xFFB6A3FF)

private val LightSecondary = Color(0xFF5E5A70)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFECEAF3)
private val LightOnSecondaryContainer = Color(0xFF2A2733)

private val LightTertiary = Color(0xFF9B3F7E)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFFE3F1)
private val LightOnTertiaryContainer = Color(0xFF3E0A2B)

internal val LightError = Color(0xFFC4302B) // danger
internal val LightOnError = Color(0xFFFFFFFF) // on-danger
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)

internal val LightSurface = Color(0xFFFFFFFF) // surface
private val LightOnSurface = Color(0xFF15141A) // ink
private val LightSurfaceVariant = Color(0xFFF5F5F8) // surface-container
private val LightOnSurfaceVariant = Color(0xFF6B6878) // ink-muted
private val LightOutline = Color(0xFF8D8A99) // outline
private val LightOutlineVariant = Color(0xFFECECF1) // hairline
private val LightScrim = Color(0xFF000000)
private val LightInverseSurface = Color(0xFF2A2733)
private val LightInverseOnSurface = Color(0xFFF2F1F6)
private val LightSurfaceDim = Color(0xFFE8E7EE)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFFFFFFF) // surface-raised
private val LightSurfaceContainer = Color(0xFFF5F5F8) // surface-container
private val LightSurfaceContainerHigh = Color(0xFFEFEEF4)
private val LightSurfaceContainerHighest = Color(0xFFECECF1)
// endregion

// region — Dark scheme
internal val DarkPrimary = Color(0xFFB6A3FF)
internal val DarkOnPrimary = Color(0xFF1F1155)
private val DarkPrimaryContainer = Color(0xFF2E2463)
private val DarkOnPrimaryContainer = Color(0xFFE4DCFF)
private val DarkInversePrimary = Color(0xFF6C4CF1)

private val DarkSecondary = Color(0xFFC9C5D6)
private val DarkOnSecondary = Color(0xFF2F2C39)
private val DarkSecondaryContainer = Color(0xFF2A2733)
private val DarkOnSecondaryContainer = Color(0xFFE6E3EF)

private val DarkTertiary = Color(0xFFFFB0D6)
private val DarkOnTertiary = Color(0xFF5A1A44)
private val DarkTertiaryContainer = Color(0xFF7A2C5E)
private val DarkOnTertiaryContainer = Color(0xFFFFD8EA)

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)

internal val DarkSurface = Color(0xFF0E0D12)
private val DarkOnSurface = Color(0xFFF2F1F6)
private val DarkSurfaceVariant = Color(0xFF201E28)
private val DarkOnSurfaceVariant = Color(0xFFA9A6B5)
private val DarkOutline = Color(0xFF8B8898)
private val DarkOutlineVariant = Color(0xFF2A2733)
private val DarkScrim = Color(0xFF000000)
private val DarkInverseSurface = Color(0xFFF2F1F6)
private val DarkInverseOnSurface = Color(0xFF2A2733)
private val DarkSurfaceBright = Color(0xFF2A2733)
private val DarkSurfaceContainerLowest = Color(0xFF0A090D)
private val DarkSurfaceContainerLow = Color(0xFF17151D)
private val DarkSurfaceContainer = Color(0xFF201E28)
private val DarkSurfaceContainerHigh = Color(0xFF25222E)
private val DarkSurfaceContainerHighest = Color(0xFF2A2733)
// endregion

internal val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    surfaceBright = LightSurface,
    surfaceDim = LightSurfaceDim,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

internal val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurface,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)
