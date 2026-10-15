package com.raumanian.thirtysix.browser.core.domain.model

// Cycle prevention for the [parentId] tree lives in `BookmarkRepositoryImpl`.
data class BookmarkFolder(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val createdAt: Long,
)
