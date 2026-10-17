package com.raumanian.thirtysix.browser.core.domain.util

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.TabNeighbours
import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {

    private fun tab(id: Long, position: Int, createdAt: Long = 0L, lastActiveAt: Long = 0L) = Tab(
        id = id,
        url = "https://t$id.example/",
        title = "",
        position = position,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt,
        isIncognito = id < 0L,
    )

    private fun List<Tab>.ids() = map(Tab::id)

    @Test
    fun `opening order follows position, not activity`() {
        val tabs = listOf(tab(3, 2, lastActiveAt = 99), tab(1, 0), tab(2, 1, lastActiveAt = 50))

        assertEquals(listOf(1L, 2L, 3L), TabOrder.inOpeningOrder(tabs).ids())
    }

    @Test
    fun `equal positions fall back to creation time, then to the id's magnitude`() {
        val tabs = listOf(
            tab(5, 0, createdAt = 2),
            tab(4, 0, createdAt = 1),
            tab(-3, 0, createdAt = 1),
            tab(-2, 0, createdAt = 1),
        )

        assertEquals(listOf(-2L, -3L, 4L, 5L), TabOrder.inOpeningOrder(tabs).ids())
    }

    @Test
    fun `neighbours in the middle and at both ends`() {
        val tabs = listOf(tab(1, 0), tab(2, 1), tab(3, 2))

        assertEquals(TabNeighbours(tabs[0], tabs[2]), TabOrder.neighbours(tabs, 2L))
        assertEquals(TabNeighbours(null, tabs[1]), TabOrder.neighbours(tabs, 1L))
        assertEquals(TabNeighbours(tabs[1], null), TabOrder.neighbours(tabs, 3L))
    }

    @Test
    fun `a single tab has no neighbours`() {
        assertEquals(TabNeighbours.NONE, TabOrder.neighbours(listOf(tab(1, 0)), 1L))
    }

    @Test
    fun `neighbours never cross between normal and incognito tabs`() {
        val n1 = tab(1, 0)
        val n2 = tab(2, 1)
        val i1 = tab(-1, 0)
        val i2 = tab(-2, 1)
        val tabs = listOf(i2, n2, i1, n1)

        assertEquals(TabNeighbours(n1, null), TabOrder.neighbours(tabs, 2L))
        assertEquals(TabNeighbours(null, i2), TabOrder.neighbours(tabs, -1L))
    }

    @Test
    fun `an unknown or missing active tab has no neighbours`() {
        val tabs = listOf(tab(1, 0), tab(2, 1))

        assertEquals(TabNeighbours.NONE, TabOrder.neighbours(tabs, 9L))
        assertEquals(TabNeighbours.NONE, TabOrder.neighbours(tabs, null))
    }
}
