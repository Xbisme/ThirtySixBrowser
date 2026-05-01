package com.raumanian.thirtysix.browser.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun has_three_values() {
        assertEquals(3, ThemeMode.entries.size)
    }

    @Test
    fun contains_Light_Dark_System() {
        val values = ThemeMode.entries.toSet()
        assertTrue(values.contains(ThemeMode.Light))
        assertTrue(values.contains(ThemeMode.Dark))
        assertTrue(values.contains(ThemeMode.System))
    }
}
