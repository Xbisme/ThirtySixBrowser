package com.raumanian.thirtysix.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 022 data-model §1 — nine fixed levels; anything else decodes to 100% (I-1, I-2). */
class TextSizeTest {

    @Test
    fun `there are exactly the nine levels, in order`() {
        assertEquals(listOf(50, 75, 90, 100, 110, 125, 150, 175, 200), TextSize.entries.map { it.percent })
    }

    @Test
    fun `a missing or unknown value decodes to 100 percent`() {
        assertEquals(TextSize.Percent100, TextSize.fromPercentOrDefault(null))
        assertEquals(TextSize.Percent100, TextSize.fromPercentOrDefault(0))
        assertEquals(TextSize.Percent100, TextSize.fromPercentOrDefault(130))
    }

    @Test
    fun `every level round-trips through its percentage`() {
        TextSize.entries.forEach { assertEquals(it, TextSize.fromPercentOrDefault(it.percent)) }
    }
}
