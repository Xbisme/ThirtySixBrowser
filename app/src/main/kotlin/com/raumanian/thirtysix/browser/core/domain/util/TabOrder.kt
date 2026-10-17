package com.raumanian.thirtysix.browser.core.domain.util

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.TabNeighbours
import kotlin.math.abs

/** The order tabs are shown and swiped in: the order they were opened, which switching never changes. */
object TabOrder {

    private val openingOrder: Comparator<Tab> = compareBy<Tab>({ it.position }, { it.createdAt }, { abs(it.id) })

    fun inOpeningOrder(tabs: List<Tab>): List<Tab> = tabs.sortedWith(openingOrder)

    fun neighbours(tabs: List<Tab>, activeId: Long?): TabNeighbours {
        val active = tabs.firstOrNull { it.id == activeId } ?: return TabNeighbours.NONE
        val sameKind = inOpeningOrder(tabs.filter { it.isIncognito == active.isIncognito })
        val index = sameKind.indexOf(active)
        return TabNeighbours(previous = sameKind.getOrNull(index - 1), next = sameKind.getOrNull(index + 1))
    }

    /** The tab that becomes active when [closedId] closes: the one after it, or the one before it if it was last. */
    fun heirOf(tabs: List<Tab>, closedId: Long): Tab? {
        val (previous, next) = neighbours(tabs, closedId)
        return next ?: previous
    }
}
