// Spec 019 — the design system's soft, upward-bar and FAB-glow shadows (research R3).
// Material's elevation shadow cannot express a two-layer soft shadow or a coloured glow, so
// these draw a blurred shadow layer behind the element. The draw happens once per frame the
// element is drawn; nothing here reads changing state, so it never causes recomposition.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val BAR_SHADOW_RISE_DP = 6
private val SoftBlur = 24.dp
private val SoftOffsetY = 8.dp
private val BarOffsetY = -BAR_SHADOW_RISE_DP.dp
private val FabBlur = 28.dp
private val FabOffsetY = 10.dp
private val FallbackElevation = 6.dp

/**
 * A blurred shadow of [shape] in [color], drawn behind the element. On API 28+ the blur is a
 * hardware `setShadowLayer`; below that the platform elevation shadow is the fallback.
 */
fun Modifier.dropShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.Transparent.toArgb()
                setShadowLayer(blur.toPx(), 0f, offsetY.toPx(), color.toArgb())
            }
            drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
        }
    } else {
        shadow(elevation = FallbackElevation, shape = shape, clip = false)
    }

/** The search bar, shortcut and tab cards, dialogs. */
fun Modifier.softShadow(shape: Shape, color: Color): Modifier =
    dropShadow(shape, color, SoftBlur, SoftOffsetY)

/** The bottom bar and sheets: shadow cast upward. */
fun Modifier.barShadow(shape: Shape, color: Color): Modifier =
    dropShadow(shape, color, SoftBlur, BarOffsetY)

/** The new-tab action's violet glow. */
fun Modifier.fabGlow(shape: Shape, color: Color): Modifier =
    dropShadow(shape, color, FabBlur, FabOffsetY)
