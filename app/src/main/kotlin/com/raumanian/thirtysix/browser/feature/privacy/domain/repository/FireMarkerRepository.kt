package com.raumanian.thirtysix.browser.feature.privacy.domain.repository

import com.raumanian.thirtysix.browser.core.domain.util.Result
/** Durable "a Fire run has started and not finished" marker, so an interrupted run is finished on next start. */
interface FireMarkerRepository {
    /** `true` when a run was interrupted. An unreadable store reads as `false`. */
    suspend fun isFireInProgress(): Boolean

    /** Returns only once the value is written to disk. */
    suspend fun setFireInProgress(value: Boolean): Result<Unit>
}
