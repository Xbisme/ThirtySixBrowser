package com.raumanian.thirtysix.browser.core.domain.model

data class Tab(
    val id: Long,
    val url: String,
    val title: String,
    /** Insertion order; only a tiebreaker until drag-to-reorder exists. */
    val position: Int,
    val createdAt: Long,
    /** The active tab is the one with the latest value. */
    val lastActiveAt: Long,
    /**
     * Incognito tabs live only in memory and use negative ids, so they never collide with persisted
     * Tabs when the two sources are merged.
     */
    val isIncognito: Boolean = false,
)
