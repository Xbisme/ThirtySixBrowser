package com.raumanian.thirtysix.browser.core.domain.model

data class HistoryEntry(
    val id: Long,
    /** Final settled URL of a successful page load. */
    val url: String,
    /** May be empty; the hostname is shown instead. */
    val title: String,
    val visitedAt: Long,
)
