package com.raumanian.thirtysix.browser.core.domain.model

// Only causes a user can act on get their own variant; everything else is [Generic].
sealed class DownloadFailureCause {

    /** The device ran out of room mid-transfer. Actionable: free space and retry. */
    data object InsufficientSpace : DownloadFailureCause()

    /** Connectivity failed and the platform gave up. Actionable: reconnect and retry. */
    data object NetworkFailure : DownloadFailureCause()

    /** Anything else the platform reported. Last-resort bucket. */
    data object Generic : DownloadFailureCause()
}
