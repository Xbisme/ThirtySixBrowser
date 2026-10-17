package com.raumanian.thirtysix.browser.core.domain.model

/** The tabs of the active tab's kind just before and just after it in opening order. */
data class TabNeighbours(val previous: Tab?, val next: Tab?) {
    companion object {
        val NONE: TabNeighbours = TabNeighbours(previous = null, next = null)
    }
}
