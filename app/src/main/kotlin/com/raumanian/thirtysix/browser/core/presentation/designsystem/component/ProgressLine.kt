@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
/**
 * Page-load and download progress. [progress] is read only inside `drawBehind`, so an update
 * redraws this line without recomposing. Pass a lambda that reads the state itself
 * (`{ state.value }`), not one wrapping a value already read in composition.
 */
@Composable
fun ProgressLine(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    thickness: Dp = Dimens.progressLine,
    trackColor: Color = Color.Transparent,
) {
    val colors = ThirtySixTheme.colors
    val start = colors.fabStart
    val end = colors.fabEnd
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                if (trackColor != Color.Transparent) {
                    drawRoundRect(color = trackColor, cornerRadius = radius)
                }
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(start, end), endX = size.width),
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}
