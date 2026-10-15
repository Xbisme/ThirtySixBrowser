package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.ProtectionData
/** The protection lists bundled in the APK, parsed once per process off the main thread, never fetched. */
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
