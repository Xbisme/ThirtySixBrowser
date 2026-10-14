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
 * Spec 019 — page-load and download progress (design system ProgressBar).
 *
 * **Phase contract (Constitution v1.4.0 §V rules 1–2, contracts C6):** [progress] is a lambda and
 * is invoked only inside `drawBehind`, so a progress update invalidates one draw of this line
 * and recomposes nothing. Callers MUST pass a lambda that reads the state itself
 * (`{ state.value }`), never one that wraps a value already read in composition.
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
