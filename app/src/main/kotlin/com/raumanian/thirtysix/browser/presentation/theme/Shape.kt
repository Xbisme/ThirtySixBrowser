// Spec 019 — shape scale from the design system (research R3), replacing Spec 003's
// Material 3 defaults. radius-xs 6 · sm 10 · md 14 · lg 20 · xl 28 · sheet 32 · full.

package com.raumanian.thirtysix.browser.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val ThirtySixShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Shapes the Material 3 scale has no slot for. Read through `ThirtySixTheme.shapes`. */
@Immutable
data class ThirtySixExtraShapes(
    /** Top corners of bottom sheets (radius-sheet). */
    val sheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    /** Bottom bar: top corners at radius-lg. */
    val bar: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    /** Pills: search bar, chips, buttons, icon buttons, the new-tab action. */
    val full: Shape = CircleShape,
)
