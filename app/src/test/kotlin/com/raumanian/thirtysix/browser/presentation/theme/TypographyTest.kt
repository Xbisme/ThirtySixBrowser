package com.raumanian.thirtysix.browser.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {

    @Test
    fun headlineMedium_uses_Poppins() {
        assertEquals(Poppins, ThirtySixTypography.headlineMedium.fontFamily)
    }

    @Test
    fun displayLarge_uses_Poppins() {
        assertEquals(Poppins, ThirtySixTypography.displayLarge.fontFamily)
    }

    @Test
    fun titleLarge_uses_Poppins() {
        assertEquals(Poppins, ThirtySixTypography.titleLarge.fontFamily)
    }

    @Test
    fun titleMedium_uses_Inter() {
        assertEquals(Inter, ThirtySixTypography.titleMedium.fontFamily)
    }

    @Test
    fun bodyLarge_uses_Inter() {
        assertEquals(Inter, ThirtySixTypography.bodyLarge.fontFamily)
    }

    @Test
    fun labelLarge_uses_Inter() {
        assertEquals(Inter, ThirtySixTypography.labelLarge.fontFamily)
    }
}
