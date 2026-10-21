package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.feature.browser.domain.model.ExternalLinkDecision
import com.raumanian.thirtysix.browser.feature.browser.domain.model.ExternalNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every row of the hand-off table, and the schemes never handed off. */
class ExternalLinkPolicyTest {

    private fun nav(
        url: String = "market://details?id=x",
        mainFrame: Boolean = true,
        gesture: Boolean = true,
        incognito: Boolean = false,
    ) = ExternalNavigation(
        url = url,
        scheme = url.substringBefore(':').lowercase(),
        isMainFrame = mainFrame,
        hasGesture = gesture,
        isIncognito = incognito,
    )

    @Test
    fun `a sub-frame is dropped, tapped or not, normal or incognito`() {
        for ((gesture, incognito) in FLAG_PAIRS) {
            val decision = ExternalLinkPolicy.decide(nav(mainFrame = false, gesture = gesture, incognito = incognito))
            assertEquals(ExternalLinkDecision.Drop, decision)
        }
    }

    @Test
    fun `the main frame without a tap is offered`() {
        assertEquals(ExternalLinkDecision.Offer, ExternalLinkPolicy.decide(nav(gesture = false)))
        assertEquals(ExternalLinkDecision.Offer, ExternalLinkPolicy.decide(nav(gesture = false, incognito = true)))
    }

    @Test
    fun `the main frame after a tap launches in a normal tab`() {
        assertEquals(ExternalLinkDecision.Launch, ExternalLinkPolicy.decide(nav()))
    }

    @Test
    fun `the main frame after a tap asks first in incognito`() {
        assertEquals(ExternalLinkDecision.Confirm, ExternalLinkPolicy.decide(nav(incognito = true)))
    }

    @Test
    fun `javascript, file and content are dropped in every row`() {
        val urls = listOf("javascript:alert(1)", "file:///sdcard/a", "content://x/y", "JavaScript:alert(1)")
        for (url in urls) {
            for ((mainFrame, flags) in listOf(true, false).flatMap { main -> FLAG_PAIRS.map { main to it } }) {
                val decision = ExternalLinkPolicy.decide(nav(url, mainFrame, flags.first, flags.second))
                assertEquals(url, ExternalLinkDecision.Drop, decision)
            }
        }
    }

    @Test
    fun `a redirect counts as tapped only when its navigation was tapped`() {
        assertTrue(ExternalLinkPolicy.isTapped(hasGesture = false, isRedirect = true, navigationTapped = true))
        assertFalse(ExternalLinkPolicy.isTapped(hasGesture = false, isRedirect = true, navigationTapped = false))
    }

    @Test
    fun `a plain navigation keeps the gesture it reports`() {
        assertFalse(ExternalLinkPolicy.isTapped(hasGesture = false, isRedirect = false, navigationTapped = true))
        for ((isRedirect, navigationTapped) in FLAG_PAIRS) {
            assertTrue(ExternalLinkPolicy.isTapped(hasGesture = true, isRedirect, navigationTapped))
        }
    }

    private companion object {
        val FLAG_PAIRS = listOf(true to true, true to false, false to true, false to false)
    }
}
