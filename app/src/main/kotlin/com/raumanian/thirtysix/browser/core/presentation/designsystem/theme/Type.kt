// Spec 003 — Typography mapping: Poppins (heading) + Inter (body), bundled local in res/font/.
// Override only fontFamily + fontWeight on Material3 default Typography; size/lineHeight/letterSpacing preserved.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.raumanian.thirtysix.browser.R

val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

private val Default = Typography()

private const val HEADLINE_LARGE_TRACKING_SP = -0.5f
private const val HEADLINE_SMALL_TRACKING_SP = -0.3f
private const val TITLE_LARGE_TRACKING_SP = -0.2f

internal val ThirtySixTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    displayMedium = Default.displayMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    displaySmall = Default.displaySmall.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    // Spec 019 — tighter tracking on display-face headings, per the design system's type group.
    headlineLarge = Default.headlineLarge.copy(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = HEADLINE_LARGE_TRACKING_SP.sp,
    ),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = HEADLINE_SMALL_TRACKING_SP.sp,
    ),
    titleLarge = Default.titleLarge.copy(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        letterSpacing = TITLE_LARGE_TRACKING_SP.sp,
    ),
    titleMedium = Default.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    titleSmall = Default.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    bodySmall = Default.bodySmall.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    labelLarge = Default.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = Default.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)
