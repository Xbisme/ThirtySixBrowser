package com.raumanian.thirtysix.browser.core.presentation.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The violet palette's invariants and the 4.5:1 contrast floor for every text/surface pair the
 * screens actually use, in both themes.
 */
class ThirtySixColorsTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + LUMINANCE_OFFSET
        val lb = b.luminance() + LUMINANCE_OFFSET
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun assertText(name: String, text: Color, surface: Color) {
        val ratio = contrast(text, surface)
        assertTrue("$name is $ratio:1, needs $TEXT_MIN:1", ratio >= TEXT_MIN)
    }

    private fun assertGraphic(name: String, mark: Color, surface: Color) {
        val ratio = contrast(mark, surface)
        assertTrue("$name is $ratio:1, needs $GRAPHIC_MIN:1", ratio >= GRAPHIC_MIN)
    }

    @Test
    fun incognitoPlum_isIdenticalInBothThemes() {
        assertEquals(LightThirtySixColors.surfaceIncognito, DarkThirtySixColors.surfaceIncognito)
        assertEquals(LightThirtySixColors.onIncognito, DarkThirtySixColors.onIncognito)
    }

    @Test
    fun lightAndDark_differWhereTheTokensDiffer() {
        assertNotEquals(LightColorScheme.primary, DarkColorScheme.primary)
        assertNotEquals(LightColorScheme.surface, DarkColorScheme.surface)
        assertNotEquals(LightThirtySixColors.heroDeep, DarkThirtySixColors.heroDeep)
    }

    @Test
    fun lightSurface_isWhite() {
        assertEquals(Color.White, LightColorScheme.surface)
    }

    @Test
    fun textPairs_meetContrastInBothThemes() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (theme, s) ->
            listOf(s.surface, s.surfaceContainerLow, s.surfaceContainer).forEachIndexed { i, ground ->
                assertText("$theme onSurface/$i", s.onSurface, ground)
                assertText("$theme onSurfaceVariant/$i", s.onSurfaceVariant, ground)
                assertText("$theme primary/$i", s.primary, ground)
            }
            assertText("$theme onPrimary", s.onPrimary, s.primary)
            assertText("$theme onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer)
            assertText("$theme error", s.error, s.surface)
            assertText("$theme onError", s.onError, s.error)
            assertGraphic("$theme outline", s.outline, s.surface)
        }
    }

    @Test
    fun extendedPairs_meetContrastInBothThemes() {
        listOf(
            Triple("light", LightThirtySixColors, LightColorScheme),
            Triple("dark", DarkThirtySixColors, DarkColorScheme),
        ).forEach { (theme, x, s) ->
            assertText("$theme onIncognito", x.onIncognito, x.surfaceIncognito)
            assertText("$theme onGlass on hero", x.onGlass, x.heroDeep)
            assertText("$theme positive", x.positive, s.surface)
            assertGraphic("$theme sky ink", x.tileSkyInk, x.tileSky)
            assertGraphic("$theme sun ink", x.tileSunInk, x.tileSun)
            assertGraphic("$theme mint ink", x.tileMintInk, x.tileMint)
            assertGraphic("$theme peach ink", x.tilePeachInk, x.tilePeach)
            assertGraphic("$theme new-tab icon on gradient start", s.onPrimary, x.fabStart)
            assertGraphic("$theme new-tab icon on gradient end", s.onPrimary, x.fabEnd)
        }
    }

    private companion object {
        const val LUMINANCE_OFFSET = 0.05f
        const val TEXT_MIN = 4.5
        const val GRAPHIC_MIN = 3.0
    }
}
