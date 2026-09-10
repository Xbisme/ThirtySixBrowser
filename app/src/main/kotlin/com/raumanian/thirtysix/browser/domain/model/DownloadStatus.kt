package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 015 — the live state of one transfer.
 *
 * **Never persisted** (FR-015, FR-024c). Read from the platform download service at
 * display time, paired with a [DownloadRecord] to form a [DownloadListItem], and
 * discarded. Modelled as a sealed type so that "which fields are meaningful" is carried
 * by the type rather than by nullability convention.
 */
sealed class DownloadStatus {

    /** Accepted by the platform, not yet transferring. */
    data object Pending : DownloadStatus()

    /**
     * In flight.
     *
     * @property totalBytes null when the server declared no length — the UI then shows
     *   indeterminate progress rather than a fabricated percentage.
     */
    data class Running(val bytesSoFar: Long, val totalBytes: Long?) : DownloadStatus()

    /** The platform paused it (connectivity lost, waiting for an allowed network). */
    data class Paused(val bytesSoFar: Long, val totalBytes: Long?) : DownloadStatus()

    /**
     * Finished successfully.
     *
     * @property totalBytes the final size the platform reported, or null when it did not
     *   know one — a server that declared no length leaves the platform with nothing to
     *   report even after the transfer succeeds. Carried here rather than dropped because
     *   FR-020 requires every row to show a size, and *completed* is the common case: a
     *   status that forgets the size makes the list show "unknown" for almost every entry.
     */
    data class Complete(val totalBytes: Long? = null) : DownloadStatus()

    /** Ended in failure. */
    data class Failed(val cause: DownloadFailureCause) : DownloadStatus()

    /** Removed by the user before completion. */
    data object Cancelled : DownloadStatus()

    /**
     * **Derived, not reported.** Emitted by the FR-024a fallback when the platform does
     * not recognise the transfer handle *and* the recorded file is absent.
     *
     * Note the accepted limitation recorded in the spec as A15: a download that failed
     * long ago and was subsequently pruned by the platform is indistinguishable from one
     * that succeeded and whose file the user later deleted. Both land here, and the
     * remedy offered to the user is the same in either case.
     */
    data object Missing : DownloadStatus()

    /**
     * Whether this state is terminal — the transfer will not change on its own.
     *
     * Single source of truth for two separate rules that must never disagree: the
     * FR-034a action-availability matrix, and FR-036a's visibility of the inline cancel
     * control. Defining it once is why they cannot drift apart.
     */
    val isTerminal: Boolean
        get() = when (this) {
            is Complete, Cancelled, Missing -> true
            is Failed -> true
            Pending, is Running, is Paused -> false
        }

    /** Convenience inverse of [isTerminal]. */
    val isInFlight: Boolean get() = !isTerminal
}
