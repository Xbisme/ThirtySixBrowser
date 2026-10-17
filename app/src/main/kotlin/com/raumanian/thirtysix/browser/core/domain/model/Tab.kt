package com.raumanian.thirtysix.browser.core.domain.model

data class Tab(
    val id: Long,
    val url: String,
    val title: String,
    /** Opening order: the order tabs are shown and swiped in. Switching tabs never changes it. */
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
