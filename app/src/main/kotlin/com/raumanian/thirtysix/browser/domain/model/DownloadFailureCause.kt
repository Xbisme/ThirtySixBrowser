package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 015 FR-022 — why a download ended in failure.
 *
 * Deliberately small. Only causes a user can *act on* get their own variant; everything
 * else collapses into [Generic] rather than surfacing a platform error code the user
 * cannot use. Mirrors the shape of Spec 007's `ErrorReason`.
 *
 * Kept free of Android imports so the domain layer stays pure (Constitution §IV). The
 * `@StringRes` mapping lives beside the presentation layer in
 * `presentation/downloads/DownloadFailureCauseExt.kt`, exactly as `ErrorReason`'s own
 * mapping is a separate extension rather than a member.
 */
sealed class DownloadFailureCause {

    /** The device ran out of room mid-transfer. Actionable: free space and retry. */
    data object InsufficientSpace : DownloadFailureCause()

    /** Connectivity failed and the platform gave up. Actionable: reconnect and retry. */
    data object NetworkFailure : DownloadFailureCause()

    /** Anything else the platform reported. Last-resort bucket. */
    data object Generic : DownloadFailureCause()
}
