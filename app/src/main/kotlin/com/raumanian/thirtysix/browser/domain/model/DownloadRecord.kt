package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 015 — the app's durable record of one download it initiated.
 *
 * Holds identity and metadata only. Live transfer state (progress, byte counts,
 * running/paused) is deliberately absent: it is read from the platform download
 * service at display time and never persisted (FR-015). See
 * [com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway].
 *
 * **Record-existence invariant (FR-008b)**: an instance is persisted only after the
 * platform service accepted the transfer and returned a [transferHandle]. There is no
 * such thing as a record without a handle — which is what makes the FR-024a status
 * inference sound, because "handle not recognised" can then only mean *forgotten*,
 * never *never-started*.
 *
 * **No incognito concept (FR-014a)**: downloads started from incognito tabs are recorded
 * identically to any other. There is deliberately no field for it.
 *
 * @property id the app's own row id; `0L` before persistence.
 * @property sourceUrl the address the file was fetched from. Surfaced by "copy link"
 *   (FR-033); never re-fetched automatically.
 * @property fileName the sanitised name as written to disk. Already reduced to a single
 *   path segment before it reaches this model (FR-004, FR-005).
 * @property mimeType the determined content type (FR-006). Empty string when nothing
 *   trustworthy was available — never null, so the mapper stays total.
 * @property createdAt epoch milliseconds at the moment the platform accepted the
 *   transfer. Drives reverse-chronological ordering (FR-019) and the displayed time.
 * @property transferHandle the platform service's handle for the transfer. The join key
 *   for every live-status read and the argument to cancellation (FR-036). Named for the
 *   role rather than the platform class, so the domain vocabulary does not bind itself to
 *   one implementation (FR-054).
 * @property localUri content URI of the finished file, recorded once the transfer
 *   completes. Null while in flight. Used to open the file (FR-025, FR-026) and to test
 *   file presence (FR-024a, FR-029).
 */
data class DownloadRecord(
    val id: Long,
    val sourceUrl: String,
    val fileName: String,
    val mimeType: String,
    val createdAt: Long,
    val transferHandle: Long,
    val localUri: String?,
)
