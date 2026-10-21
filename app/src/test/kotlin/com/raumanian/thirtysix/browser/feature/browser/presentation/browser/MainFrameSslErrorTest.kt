package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainFrameSslErrorTest {

    @Test
    fun `an embedded resource's certificate error does not fail the page`() {
        assertFalse(isMainFrameSslError(errorUrl = "https://cdn.bad/x.png", mainFrameUrl = "https://good.example/"))
    }

    @Test
    fun `the main page's certificate error fails the page`() {
        assertTrue(isMainFrameSslError(errorUrl = "https://good.example/", mainFrameUrl = "https://good.example/"))
    }

    @Test
    fun `an error before the page started counts as the main page`() {
        assertTrue(isMainFrameSslError(errorUrl = "https://good.example/", mainFrameUrl = null))
    }
}
