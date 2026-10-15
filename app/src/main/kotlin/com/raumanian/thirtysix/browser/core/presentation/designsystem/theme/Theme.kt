@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private const val DYNAMIC_FAB_END_LIGHTEN = 0.25f

val LocalThirtySixShapes = staticCompositionLocalOf { ThirtySixExtraShapes() }

/**
 * App theme. Under dynamic color the wallpaper supplies primary, its containers and the surfaces;
 * of the extended tokens only the hero's main glow and the action gradient follow it, so danger,
 * the pastels, the incognito plum and the hero's dark base stay fixed and the hero is always dark.
 */
@Composable
fun ThirtySixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseExtended = if (darkTheme) DarkThirtySixColors else LightThirtySixColors
    val colorScheme: ColorScheme
    val extended: ThirtySixColors
    if (useDynamic) {
        val context = LocalContext.current
        val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val fixed = if (darkTheme) DarkColorScheme else LightColorScheme
        colorScheme = dynamic.copy(
            error = fixed.error,
            onError = fixed.onError,
            errorContainer = fixed.errorContainer,
            onErrorContainer = fixed.onErrorContainer,
        )
        extended = remember(dynamic.primary, baseExtended) {
            baseExtended.copy(
                heroViolet = dynamic.primary,
                fabStart = dynamic.primary,
                fabEnd = lerp(dynamic.primary, Color.White, DYNAMIC_FAB_END_LIGHTEN),
            )
        }
    } else {
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
        extended = baseExtended
    }

    CompositionLocalProvider(
        LocalThirtySixColors provides extended,
        LocalThirtySixShapes provides ThirtySixExtraShapes(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ThirtySixTypography,
            shapes = ThirtySixShapes,
            content = content,
        )
    }
}

/** Access to the design system's tokens that Material 3 has no slot for. */
object ThirtySixTheme {
    val colors: ThirtySixColors
        @Composable @ReadOnlyComposable
        get() = LocalThirtySixColors.current

    val shapes: ThirtySixExtraShapes
        @Composable @ReadOnlyComposable
        get() = LocalThirtySixShapes.current
}
