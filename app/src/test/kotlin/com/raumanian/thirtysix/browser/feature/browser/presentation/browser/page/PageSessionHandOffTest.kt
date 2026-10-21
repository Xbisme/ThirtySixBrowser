package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The offer's Open is decided as a tap on the current page. */
class PageSessionHandOffTest {

    private val launches = mutableListOf<String>()

    private fun session(incognito: Boolean) =
        PageSession(PageRequestQueue()).apply { onTabChanged(newTabId = 1L, incognito = incognito) }

    @Test
    fun `a normal tab launches at once`() {
        val session = session(incognito = false)

        session.handOffTapped(TARGET, origin = { ORIGIN }) { launches += TARGET }

        assertEquals(listOf(TARGET), launches)
        assertNull(session.queue.head.value)
    }

    @Test
    fun `an incognito tab asks first and launches on open`() {
        val session = session(incognito = true)

        session.handOffTapped(TARGET, origin = { ORIGIN }) { launches += TARGET }

        assertTrue(launches.isEmpty())
        val ask = session.queue.head.value as PageRequest.OpenExternalApp
        assertEquals(ORIGIN, ask.origin)
        ask.open()
        assertEquals(listOf(TARGET), launches)
    }

    @Test
    fun `a javascript address is never launched`() {
        session(incognito = false).handOffTapped("javascript:alert(1)", origin = { ORIGIN }) { launches += "js" }

        assertTrue(launches.isEmpty())
    }

    private companion object {
        const val TARGET = "market://details?id=x"
        const val ORIGIN = "https://a.org"
    }
}
