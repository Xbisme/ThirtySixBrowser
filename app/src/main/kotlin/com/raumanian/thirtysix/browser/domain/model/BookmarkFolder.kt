package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 013 — domain representation of a bookmark folder.
 *
 * Pure Kotlin per Constitution §IV. Self-referencing tree via [parentId];
 * cycle prevention enforced in `MoveFolderUseCase` (research R6).
 *
 * @property id auto-generated primary key; `0L` when not yet persisted.
 * @property name non-blank; capped by `BrowserLimits.MAX_FOLDER_NAME_LENGTH`.
 * @property parentId parent folder, or null for root level.
 * @property createdAt epoch millis at insert time.
 */
data class BookmarkFolder(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val createdAt: Long,
)
