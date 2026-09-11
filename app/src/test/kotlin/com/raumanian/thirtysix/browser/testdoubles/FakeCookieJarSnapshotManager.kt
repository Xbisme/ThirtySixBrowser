package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager

/**
 * Spec 012 — hand-rolled fake of [CookieJarSnapshotManager] for unit tests.
 *
 * Tracks invocation history (capture / restore counts + last-captured origins)
 * for assertion in `IncognitoTabRepositoryImplTest` and `TabsViewModelTest`
 * without touching the platform `CookieManager` (which would require
 * Robolectric).
 *
 * Spec 016 T065 — adds [discardSetAsideCookies] and an optional [callLog] shared with other
 * fakes, so `ClearBrowsingDataUseCaseTest` can assert the discard runs before the cookie wipe.
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
