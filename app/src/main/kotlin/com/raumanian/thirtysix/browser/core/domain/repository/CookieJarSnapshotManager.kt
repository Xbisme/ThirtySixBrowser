package com.raumanian.thirtysix.browser.core.domain.repository

/**
 * Snapshot and restore of the process-wide cookie jar around an incognito session, for engines
 * without per-profile isolation: capture before the first incognito tab, restore after the last.
 * Both are idempotent, and the implementation never throws; failure degrades to wiped cookies.
 */
interface CookieJarSnapshotManager {

    /** Keeps an existing snapshot, so a 0 → 1 → 2 → 1 incognito tab sequence never re-captures. */
    suspend fun captureSnapshot(origins: List<String>)

    /**
     * Wipes the live jar, including incognito cookies, and writes the snapshot back.
     * With no snapshot held it does nothing, and wipes nothing. False when the wipe timed out or
     * failed, so incognito cookies may remain in the jar.
     */
    suspend fun restoreSnapshot(): Boolean

    fun hasSnapshot(): Boolean

    /**
     * Replaces a held snapshot with `CookieJarSnapshot.EMPTY`, never null: with nothing held,
     * [restoreSnapshot] skips its wipe and incognito cookies leak. Call before wiping the jar.
     */
    suspend fun discardSetAsideCookies()
}
