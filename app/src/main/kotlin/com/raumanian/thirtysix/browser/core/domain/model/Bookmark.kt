package com.raumanian.thirtysix.browser.core.domain.model

data class Bookmark(
    /** `0L` when not yet persisted. */
    val id: Long,
    val title: String,
    val url: String,
    val parentFolderId: Long?,
    val createdAt: Long,
    /** Currently equal to [createdAt]; reserved for drag-reorder. */
    val sortOrder: Long,
)
