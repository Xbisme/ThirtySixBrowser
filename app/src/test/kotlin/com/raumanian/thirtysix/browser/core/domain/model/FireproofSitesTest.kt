package com.raumanian.thirtysix.browser.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FireproofSitesTest {
    @Test
    fun `a site covers itself and its www host`() {
        assertEquals(listOf("example.com", "www.example.com"), FireproofSites.originsFor("example.com"))
    }
}
