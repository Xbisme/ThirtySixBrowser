package com.raumanian.thirtysix.browser.feature.privacy.domain.repository

import com.raumanian.thirtysix.browser.core.domain.util.Result
/**
 * Spec 024 FR-007, INV-1 — the durable "a Fire run has started and not finished" marker. A narrow
 * repository of its own, like Spec 020's invite state, so the settings fakes and the user-settings
 * model stay untouched.
 */
interface FireMarkerRepository {
    /** `true` when a run was interrupted. An unreadable store reads as `false`. */
    suspend fun isFireInProgress(): Boolean

    /** Returns only once the value is written to disk. */
    suspend fun setFireInProgress(value: Boolean): Result<Unit>
}
