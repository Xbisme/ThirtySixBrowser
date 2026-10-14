package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 021 T047 — every row of contracts C1.3. */
class DecidePopupUseCaseTest {

    private val decide = DecidePopupUseCase()

    @Test
    fun `a tap opens a new tab as incognito as the opener`() {
        val normal = decide(true, isOpenerIncognito = false, atTabCap = false)
        val incognito = decide(true, isOpenerIncognito = true, atTabCap = false)
        assertEquals(PopupDecision.OpenInNewTab(incognito = false), normal)
        assertEquals(PopupDecision.OpenInNewTab(incognito = true), incognito)
    }

    @Test
    fun `a tap at the tab cap gets the tab-limit message`() {
        assertEquals(PopupDecision.TabLimit, decide(true, isOpenerIncognito = false, atTabCap = true))
    }

    @Test
    fun `a window the page opened on its own is blocked, cap or not`() {
        assertEquals(PopupDecision.Blocked, decide(false, isOpenerIncognito = false, atTabCap = false))
        assertEquals(PopupDecision.Blocked, decide(false, isOpenerIncognito = true, atTabCap = true))
    }

    @Test
    fun `Open is offered only for web addresses`() {
        assertEquals("https://a.org/x", decide.openUrlFor("https://a.org/x"))
        assertEquals("http://a.org", decide.openUrlFor("http://a.org"))
        assertNull(decide.openUrlFor(null))
        assertNull(decide.openUrlFor(""))
        assertNull(decide.openUrlFor("about:blank"))
        assertNull(decide.openUrlFor("javascript:alert(1)"))
        assertNull(decide.openUrlFor("data:text/html,hi"))
    }
}
