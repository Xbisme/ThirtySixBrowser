package com.raumanian.thirtysix.browser.presentation.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTest {

    @Test
    fun xs_is_4dp() {
        assertEquals(4.dp, Spacing.xs)
    }

    @Test
    fun sm_is_8dp() {
        assertEquals(8.dp, Spacing.sm)
    }

    @Test
    fun md_is_16dp() {
        assertEquals(16.dp, Spacing.md)
    }

    @Test
    fun lg_is_24dp() {
        assertEquals(24.dp, Spacing.lg)
    }

    @Test
    fun xl_is_32dp() {
        assertEquals(32.dp, Spacing.xl)
    }
}
