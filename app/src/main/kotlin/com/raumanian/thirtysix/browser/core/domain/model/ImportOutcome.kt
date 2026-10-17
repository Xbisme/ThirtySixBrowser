package com.raumanian.thirtysix.browser.core.domain.model

data class ImportOutcome(
    val folderId: Long,
    val folderName: String,
    val bookmarksAdded: Int,
    val foldersAdded: Int,
)
