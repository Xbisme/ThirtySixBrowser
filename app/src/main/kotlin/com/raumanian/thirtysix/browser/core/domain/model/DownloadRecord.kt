package com.raumanian.thirtysix.browser.core.domain.model

/**
 * The durable record of one download. Live transfer state is read from the platform at display
 * Time and never persisted. A record is saved only after the platform returned a [transferHandle],
 * So an unrecognised handle can only mean the platform forgot it, never that it never started.
 */
data class DownloadRecord(
    val id: Long,
    val sourceUrl: String,
    val fileName: String,
    /** Empty, never null, when nothing trustworthy was available. */
    val mimeType: String,
    val createdAt: Long,
    val transferHandle: Long,
    /** Content URI of the finished file; null while in flight. */
    val localUri: String?,
)
