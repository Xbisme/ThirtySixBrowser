package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 015 — the value handed to the platform seam when starting a download.
 *
 * Constructed in the domain layer so that the FR-005 filename sanitisation is applied and
 * unit-testable on the plain JVM, before anything Android-shaped is involved.
 *
 * @property fileName MUST already be sanitised — a single path segment with no separators
 *   or parent-directory references (FR-005). Constructing this type is the point past
 *   which a server-supplied name is treated as trusted.
 * @property mimeType the determined content type; empty string when nothing trustworthy
 *   was available (FR-006).
 * @property userAgent the user agent the web engine used, so the transfer is made with
 *   the same identity as the request that triggered it.
 * @property referer the page the download was initiated from, or null.
 */
data class DownloadRequest(
    val sourceUrl: String,
    val fileName: String,
    val mimeType: String,
    val userAgent: String,
    val referer: String?,
)
