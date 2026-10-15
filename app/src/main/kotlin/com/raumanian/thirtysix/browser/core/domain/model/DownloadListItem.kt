package com.raumanian.thirtysix.browser.core.domain.model

/** A record paired with its freshly resolved live status: what a row renders. */
data class DownloadListItem(
    val record: DownloadRecord,
    val status: DownloadStatus,
)
