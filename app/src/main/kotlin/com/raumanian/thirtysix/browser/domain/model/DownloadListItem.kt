package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 015 — one [DownloadRecord] paired with its freshly-resolved [DownloadStatus].
 *
 * This is what a row renders and what the FR-034a action matrix is evaluated against.
 * It has no persistence and no identity of its own beyond the record's.
 */
data class DownloadListItem(
    val record: DownloadRecord,
    val status: DownloadStatus,
)
