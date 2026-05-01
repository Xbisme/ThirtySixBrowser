package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults
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

    @Test
    fun storageValue_roundTrips_via_fromStorageValueOrDefault() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorageValueOrDefault(mode.storageValue))
        }
    }

    @Test
    fun fromStorageValueOrDefault_unknownValue_returnsDefault() {
        assertEquals(AppDefaults.THEME_MODE, ThemeMode.fromStorageValueOrDefault("midnight"))
    }

    @Test
    fun fromStorageValueOrDefault_null_returnsDefault() {
        assertEquals(AppDefaults.THEME_MODE, ThemeMode.fromStorageValueOrDefault(null))
    }
}
