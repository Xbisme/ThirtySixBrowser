package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus

/**
 * Spec 015 FR-054 — narrow seam over the Android system download service.
 *
 * Exists so download decision logic is unit-testable on the plain JVM and `presentation/`
 * holds no Android imports. Mirrors the
 * [com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter] pattern from
 * Spec 014: interface plus concrete implementation in one file, Hilt-bound.
 *
 * **Every method is total.** Per FR-055 (Spec 012's system-service posture) each platform
 * call is defensively wrapped, so a service that is missing, disabled by the user, or
 * throwing can never crash the browser. Failure is expressed in the return type, never as
 * a propagating exception — hence [enqueue] returning a nullable handle rather than
 * throwing, and [queryStatus] returning null for "not known" rather than signalling an
 * error. Not crashing is the floor, not the goal: callers MUST surface a localized message
 * whenever a failure blocks something the user asked for (FR-008a).
 */
interface DownloadManagerGateway {

    /**
     * Hand a download to the platform service, asking it to show its own progress and
     * completion notifications (FR-047). This app never composes a notification.
     *
     * @return the transfer handle, or **null** when the service is unavailable — disabled,
     *   absent, or refusing. A null return MUST produce a localized message and MUST NOT
     *   create a record (FR-008a, FR-008b).
     */
    suspend fun enqueue(request: DownloadRequest): Long?

    /**
     * Read the live state of one transfer.
     *
     * Definitionally [queryStatuses] of one, so it is given a body here rather than being
     * reimplemented by every implementation and every test double.
     *
     * @return the status, or **null** when the service does not recognise the handle. Null
     *   is an ordinary, expected outcome — the service prunes its own records on its own
     *   schedule — resolved by callers via the file-presence fallback in FR-024a. It is
     *   never an error, and this never throws on an unknown handle.
     */
    suspend fun queryStatus(transferHandle: Long): DownloadStatus? =
        queryStatuses(listOf(transferHandle))[transferHandle]

    /**
     * Read many transfers in one round trip.
     *
     * The result contains an entry only for handles still recognised; an absent key means
     * the same as a null from [queryStatus]. Exists so the list resolves N rows with one
     * query rather than N (FR-021, SC-006).
     */
    suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus>

    /**
     * Cancel an in-flight transfer, discarding any partially written file (FR-037).
     *
     * Cancelling an already-completed transfer MUST leave the finished file intact and
     * MUST NOT report a false cancellation (FR-039).
     */
    suspend fun cancel(transferHandle: Long): Boolean

    /**
     * The content URI for a completed download, suitable for handing to another app.
     *
     * Always a content URI, never a filesystem path: since minSdk 24, exposing a `file://`
     * URI across an app boundary throws, so a raw path is fatal on every supported device
     * (FR-026).
     */
    suspend fun contentUriFor(transferHandle: Long): String?

    /**
     * Whether the downloaded file is still present and readable.
     *
     * Backs both FR-029 (deleted outside the app) and the FR-024a fallback (handle
     * forgotten, so presence decides between Complete and Missing).
     *
     * Takes [fileName] as well as the URI because the URI alone is not a usable signal in
     * the very case FR-024a exists for — see the implementation.
     */
    suspend fun fileExists(localUri: String?, fileName: String): Boolean

    /**
     * The name the file actually has on disk, which is not always the name that was
     * requested: the platform de-duplicates collisions by appending a suffix, so three
     * downloads of `dup.txt` land as `dup.txt`, `dup-1.txt` and `dup-2.txt` (FR-007).
     *
     * @return the resolved name, or null when the platform no longer knows the transfer.
     */
    suspend fun resolvedFileNameFor(transferHandle: Long): String?

    /**
     * Delete the downloaded file itself (FR-032), after the user has confirmed.
     *
     * Takes the transfer handle rather than a URI because the platform's own `remove` is
     * what actually deletes the file; a content URI only addresses the provider's row.
     *
     * @return true only when the file is **verified gone**. Reporting success without
     *   checking is how this shipped a "File deleted" message over a file that was still
     *   on disk — see the note on the implementation.
     */
    suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean

    /** Whether the platform download service is present and usable right now (FR-008a). */
    suspend fun isAvailable(): Boolean
}
