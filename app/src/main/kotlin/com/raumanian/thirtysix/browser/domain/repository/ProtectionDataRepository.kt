package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.domain.model.ProtectionData

/**
 * Spec 023 research R10 — the bundled protection lists. They are parsed once per process, off
 * the main thread, and never fetched from the network (Constitution §I, FR-001).
 */
interface ProtectionDataRepository {
    /** Starts parsing if it has not started. Never blocks. */
    fun preload()

    /** Never blocks; safe on the main thread. `null` until parsing has finished. */
    fun currentDataOrNull(): ProtectionData?

    /**
     * Blocks the calling **background** thread for at most [timeoutMs] until the data is ready.
     * `null` on timeout or parse failure: callers then go ahead unprotected (fail open).
     */
    fun awaitData(timeoutMs: Long): ProtectionData?
}
