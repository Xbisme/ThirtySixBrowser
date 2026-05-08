package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 013 — domain representation of a saved bookmark.
 *
 * Pure Kotlin per Constitution §IV (no Android imports). Maps to / from
 * `com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity` via
 * `com.raumanian.thirtysix.browser.data.mapper.BookmarkMapper`.
 *
 * @property id auto-generated primary key; `0L` when not yet persisted.
 * @property title human-readable label; non-blank after validation; capped by
 *           `BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH`.
 * @property url validated http(s) URL; capped by `BrowserLimits.MAX_BOOKMARK_URL_LENGTH`.
 * @property parentFolderId parent folder, or null for root level.
 * @property createdAt epoch millis at insert time.
 * @property sortOrder currently equal to [createdAt]; reserved for future drag-reorder.
 */
data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val parentFolderId: Long?,
    val createdAt: Long,
    val sortOrder: Long,
)
