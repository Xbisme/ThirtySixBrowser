// CONTRACT — Spec 015 downloads-manager
// Platform seam. Implementation will live at
// app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/download/DownloadManagerGateway.kt
// (interface + Android implementation in one file, mirroring ClipboardWriter from Spec 014),
// bound by Hilt in app/.../di/DownloadsModule.kt.

package com.raumanian.thirtysix.browser.data.local.download

import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus

/**
 * Narrow seam over the Android system download service — Spec 015 FR-054.
 *
 * Exists so that download decision logic is unit-testable on the plain JVM and so that
 * `presentation/` holds no Android imports. Mirrors the pattern
 * [com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter] established in
 * Spec 014 and [com.raumanian.thirtysix.browser.data.local.cache.FaviconCache] in Spec 011:
 * interface plus concrete implementation in one file, Hilt-bound.
 *
 * **Every method is total.** Per FR-055 (Spec 012's system-service posture) each platform
 * call is defensively wrapped, so a service that is missing, disabled by the user, or
 * throwing can never crash the browser. Failure is expressed in the return type, never as
 * a propagating exception — which is why [enqueue] returns a nullable handle rather than
 * throwing, and why [queryStatus] returns null for "not known" rather than signalling an
 * error. Not crashing is the floor, not the goal: callers MUST surface a localized message
 * whenever a failure blocks something the user asked for (FR-008a).
 */
interface DownloadManagerGateway {

    /**
     * Hand a download to the platform service.
     *
     * The service is asked to show its own progress and completion notifications; this app
     * never composes one (FR-047).
     *
     * @return the service's transfer handle, or **null** when the service is unavailable —
     *   disabled by the user, absent from the device, or refusing the request. A null return
     *   MUST result in a localized message and MUST NOT produce a record (FR-008a, FR-008b).
     */
    suspend fun enqueue(request: DownloadRequest): Long?

    /**
     * Read the live state of one transfer.
     *
     * @return the current status, or **null** when the service does not recognise the
     *   handle. Null is an ordinary, expected outcome — the service prunes its own records
     *   on its own schedule — and callers resolve it via the file-presence fallback in
     *   FR-024a. It is never an error, and this method never throws on an unknown handle.
     */
    suspend fun queryStatus(transferHandle: Long): DownloadStatus?

    /**
     * Read the live state of many transfers in one round trip.
     *
     * Returned map contains an entry only for handles the service still recognises;
     * absent keys carry the same meaning as a null from [queryStatus]. Exists so the list
     * screen resolves N rows with one query rather than N (FR-021, SC-006).
     */
    suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus>

    /**
     * Cancel an in-flight transfer, discarding any partially written file (FR-037).
     *
     * Cancelling a transfer that has already completed MUST leave the finished file intact
     * and MUST NOT report a false cancellation (FR-039).
     *
     * @return true when the service accepted the cancellation.
     */
    suspend fun cancel(transferHandle: Long): Boolean

    /**
     * The content URI for a completed download, suitable for handing to another app.
     *
     * Always a content URI — never a filesystem path. Since minSdk 24, exposing a `file://`
     * URI across an app boundary throws, so a raw path is not merely discouraged but fatal
     * on every supported device (FR-026).
     *
     * @return the URI as a string, or null when the service cannot supply one.
     */
    suspend fun contentUriFor(transferHandle: Long): String?

    /**
     * Whether the file behind a recorded content URI is still present and readable.
     *
     * Backs both FR-029 (the user deleted it outside the app) and the FR-024a fallback
     * (the handle is forgotten, so presence decides between Complete and Missing).
     */
    suspend fun fileExists(localUri: String): Boolean

    /**
     * Delete the downloaded file itself (FR-032), after the user has confirmed.
     *
     * @return true when the file was removed or was already gone.
     */
    suspend fun deleteFile(localUri: String): Boolean

    /** Whether the platform download service is present and enabled right now (FR-008a). */
    suspend fun isAvailable(): Boolean
}
