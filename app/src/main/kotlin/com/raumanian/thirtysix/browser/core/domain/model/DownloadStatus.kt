package com.raumanian.thirtysix.browser.core.domain.model

/** The live state of one transfer: read from the platform at display time, never persisted. */
sealed class DownloadStatus {

    /** Accepted by the platform, not yet transferring. */
    data object Pending : DownloadStatus()

    // A null totalBytes means the server declared no length: progress is shown as indeterminate.
    data class Running(val bytesSoFar: Long, val totalBytes: Long?) : DownloadStatus()

    /** The platform paused it (connectivity lost, waiting for an allowed network). */
    data class Paused(val bytesSoFar: Long, val totalBytes: Long?) : DownloadStatus()

    // Carries the size so finished rows, the common case, do not all show "unknown size".
    data class Complete(val totalBytes: Long? = null) : DownloadStatus()

    data class Failed(val cause: DownloadFailureCause) : DownloadStatus()

    /** Removed by the user before completion. */
    data object Cancelled : DownloadStatus()

    // Derived: the platform forgot the handle and the file is gone. A long-pruned failure
    // looks the same as a deleted success; the remedy offered is the same either way.
    data object Missing : DownloadStatus()

    // Drives both the row's available actions and the inline cancel, so they cannot disagree.
    val isTerminal: Boolean
        get() = when (this) {
            is Complete, Cancelled, Missing -> true
            is Failed -> true
            Pending, is Running, is Paused -> false
        }

    val isInFlight: Boolean get() = !isTerminal
}
