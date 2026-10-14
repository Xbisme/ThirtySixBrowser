@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
private const val GLOW_A_X = 0.2f
private const val GLOW_A_Y = 0.2f
private const val GLOW_B_X = 0.9f
private const val GLOW_B_Y = 0.9f
private const val GLOW_RADIUS = 0.8f
private const val BASE_DARKEN = 0.6f
private const val ACCENT_LIGHTEN = 0.35f
private const val LETTER_ALPHA = 0.22f
private const val LETTER_SIZE_SP = 72

/**
 * Spec 019 — a three-colour mesh: the art of a shortcut or tab card with no screenshot
 * (seeded from the favicon colour, research R13), and the Home hero (data-model §3).
 *
 * Brushes are built in `drawWithCache` and rebuilt only when the size or colours change, so
 * the mesh is one cached draw (research R6). An optional faint [letter] sits on top.
 */
@Composable
fun MeshArt(
    base: Color,
    glowA: Color,
    glowB: Color,
    modifier: Modifier = Modifier,
    letter: String? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.drawWithCache {
            val radius = size.maxDimension * GLOW_RADIUS
            val a = Brush.radialGradient(
                listOf(glowA, Color.Transparent),
                center = Offset(size.width * GLOW_A_X, size.height * GLOW_A_Y),
                radius = radius,
            )
            val b = Brush.radialGradient(
                listOf(glowB, Color.Transparent),
                center = Offset(size.width * GLOW_B_X, size.height * GLOW_B_Y),
                radius = radius,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(a)
                drawRect(b)
            }
        },
    ) {
        if (letter != null) {
            Text(
                text = letter,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = LETTER_SIZE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = ThirtySixTheme.colors.onGlass,
                modifier = Modifier.alpha(LETTER_ALPHA),
            )
        }
    }
}

/** A mesh derived from one seed colour (a site's favicon average). */
@Composable
fun SeededMeshArt(seed: Color, modifier: Modifier = Modifier, letter: String? = null) {
    MeshArt(
        base = lerp(seed, Color.Black, BASE_DARKEN),
        glowA = lerp(seed, Color.White, ACCENT_LIGHTEN),
        glowB = seed,
        modifier = modifier,
        letter = letter,
    )
}
