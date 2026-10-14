package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
/**
 * Fake [CookieJarSnapshotManager] that counts captures and restores without the platform
 * `CookieManager`. An optional [callLog] shared with other fakes lets a test assert the discard
 * runs before the cookie wipe.
 */
class FakeCookieJarSnapshotManager(
    private val callLog: MutableList<String> = mutableListOf(),
) : CookieJarSnapshotManager {

    var captureCount: Int = 0
        private set
    var restoreCount: Int = 0
        private set
    var discardCount: Int = 0
        private set
    var lastCapturedOrigins: List<String> = emptyList()
        private set

    private var snapshotHeld: Boolean = false

    override suspend fun captureSnapshot(origins: List<String>) {
        callLog += CALL_CAPTURE
        captureCount += 1
        lastCapturedOrigins = origins
        snapshotHeld = true
    }

    override suspend fun restoreSnapshot() {
        callLog += CALL_RESTORE
        restoreCount += 1
        snapshotHeld = false
    }

    override fun hasSnapshot(): Boolean = snapshotHeld

    /** Mirrors the real contract: a held snapshot stays held (as `EMPTY`), so this changes no flag. */
    override suspend fun discardSetAsideCookies() {
        callLog += CALL_DISCARD
        discardCount += 1
    }

    companion object {
        const val CALL_CAPTURE: String = "cookies.captureSnapshot"
        const val CALL_RESTORE: String = "cookies.restoreSnapshot"
        const val CALL_DISCARD: String = "cookies.discardSetAsideCookies"
    }
}
