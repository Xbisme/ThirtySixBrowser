package com.raumanian.thirtysix.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TabTest {

    @Test
    fun `data class equality + copy semantics`() {
        val a = Tab(
            id = 1L,
            url = "https://example.org/",
            title = "Example",
            position = 0,
            createdAt = 1_700_000_000_000L,
            lastActiveAt = 1_700_000_500_000L,
        )
        val b = a.copy()
        val c = a.copy(title = "Different")

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a.id, c.id)
        assertEquals(a.url, c.url)
    }
}
