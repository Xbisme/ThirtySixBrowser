package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager

/**
 * Spec 012 — hand-rolled fake of [CookieJarSnapshotManager] for unit tests.
 *
 * Tracks invocation history (capture / restore counts + last-captured origins)
 * for assertion in `IncognitoTabRepositoryImplTest` and `TabsViewModelTest`
 * without touching the platform `CookieManager` (which would require
 * Robolectric).
 */
class FakeCookieJarSnapshotManager : CookieJarSnapshotManager {

    var captureCount: Int = 0
        private set
    var restoreCount: Int = 0
        private set
    var lastCapturedOrigins: List<String> = emptyList()
        private set

    private var snapshotHeld: Boolean = false

    override suspend fun captureSnapshot(origins: List<String>) {
        captureCount += 1
        lastCapturedOrigins = origins
        snapshotHeld = true
    }

    override suspend fun restoreSnapshot() {
        restoreCount += 1
        snapshotHeld = false
    }

    override fun hasSnapshot(): Boolean = snapshotHeld
}
