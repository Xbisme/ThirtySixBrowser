package com.raumanian.thirtysix.browser.core.domain.repository

/**
 * Spec 015 FR-025 – FR-027, FR-054 — narrow seam over handing a file to another app.
 *
 * The third platform seam in this feature, alongside `DownloadManagerGateway` and Spec 014's
 * `ClipboardWriter`. FR-054 names external-app launching explicitly, and for the same reason
 * as the others: it keeps `Intent` out of the domain and lets the open path be tested
 * without a device.
 *
 * Total, like its siblings — a device with no handler for a file type is an ordinary
 * outcome reported in the return value, not an exception (FR-027).
 */
interface ExternalFileOpener {

    /**
     * Open [contentUri] with whatever app handles [mimeType].
     *
     * @return false when nothing on the device can open it, or the platform refused. Never
     *   throws, and in particular never lets `ActivityNotFoundException` escape.
     */
    fun open(contentUri: String, mimeType: String): Boolean
}
