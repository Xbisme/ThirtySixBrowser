package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 014 — domain representation of a single browsing-history entry.
 *
 * Pure Kotlin per Constitution §IV (no Android imports). Maps to / from
 * `com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity` via
 * `com.raumanian.thirtysix.browser.data.mapper.HistoryEntryMapper`.
 *
 * @property id auto-generated primary key; `0L` when not yet persisted.
 * @property url final settled URL of a successful page load.
 * @property title page title at load completion; may be empty (FR-009 falls back to hostname).
 * @property visitedAt epoch milliseconds at write time (device local clock).
 */
data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val visitedAt: Long,
)
