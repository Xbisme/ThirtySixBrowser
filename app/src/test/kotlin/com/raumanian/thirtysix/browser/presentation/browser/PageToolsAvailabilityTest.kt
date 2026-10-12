package com.raumanian.thirtysix.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 022 research R9 — one case per row of the availability table. */
class PageToolsAvailabilityTest {

    @Test
    fun `home and the incognito start page offer text size only`() {
        LoadingStates.forEach { state ->
            assertEquals(setOf(PageTool.TextSize), PageToolsAvailability.of(isHomeContent = true, loadingState = state))
        }
    }

    @Test
    fun `a loading page offers everything but print`() {
        val expected = setOf(PageTool.Find, PageTool.Share, PageTool.DesktopSite, PageTool.TextSize)
        assertEquals(expected, PageToolsAvailability.of(isHomeContent = false, loadingState = LoadingState.Loading))
        assertEquals(expected, PageToolsAvailability.of(isHomeContent = false, loadingState = LoadingState.Idle))
    }

    @Test
    fun `a loaded page offers all five tools`() {
        assertEquals(
            PageTool.entries.toSet(),
            PageToolsAvailability.of(isHomeContent = false, loadingState = LoadingState.Loaded),
        )
    }

    @Test
    fun `a failed page offers share, desktop site and text size`() {
        assertEquals(
            setOf(PageTool.Share, PageTool.DesktopSite, PageTool.TextSize),
            PageToolsAvailability.of(isHomeContent = false, loadingState = LoadingState.Failed(ErrorReason.Generic)),
        )
    }

    private companion object {
        val LoadingStates = listOf(
            LoadingState.Idle,
            LoadingState.Loading,
            LoadingState.Loaded,
            LoadingState.Failed(ErrorReason.Generic),
        )
    }
}
