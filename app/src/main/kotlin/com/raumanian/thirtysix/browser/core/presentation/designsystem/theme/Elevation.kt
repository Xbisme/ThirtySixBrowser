// Material's elevation shadow cannot express a soft two-layer shadow or a coloured glow, so these
// draw a blurred shadow layer behind the element.

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

/** A blurred shadow of [shape]; below API 28 the platform elevation shadow stands in. */
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

fun Modifier.softShadow(shape: Shape, color: Color): Modifier =
    dropShadow(shape, color, SoftBlur, SoftOffsetY)

/** The bottom bar and sheets: shadow cast upward. */
fun Modifier.barShadow(shape: Shape, color: Color): Modifier =
    dropShadow(shape, color, SoftBlur, BarOffsetY)

fun Modifier.fabGlow(shape: Shape, color: Color): Modifier =
    dropShadow(shape, color, FabBlur, FabOffsetY)
