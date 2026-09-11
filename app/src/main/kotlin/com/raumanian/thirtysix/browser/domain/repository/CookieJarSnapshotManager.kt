package com.raumanian.thirtysix.browser.domain.repository

/**
 * Spec 012 — manages a snapshot/restore protocol around the platform's global
 * cookie jar to compensate for the fact that `android.webkit.CookieManager` is
 * a process-wide singleton with no per-WebView isolation primitive on our
 * supported API levels (24..36).
 *
 * Pure-Kotlin interface kept in `domain/repository/` despite the manager-not-
 * repository naming because the Hilt convention places singleton platform-state
 * managers next to repository interfaces (consumers go through the use-case
 * layer regardless of the typename).
 *
 * Lifecycle binding (per FR-011a + research.md R1):
 *  - Caller MUST invoke [captureSnapshot] BEFORE the first incognito tab in a
 *    session is created (the `0 → 1` transition).
 *  - Caller MUST invoke [restoreSnapshot] AFTER the last incognito tab is
 *    removed from in-memory state (the `1 → 0` transition).
 *  - Both methods are idempotent: capturing twice preserves the first
 *    snapshot; restoring when no snapshot is held is a no-op (defensive
 *    against lifecycle-event reordering).
 *
 * Implementation lives in `data/local/cookies/CookieJarSnapshotManagerImpl.kt`
 * and wraps `CookieManager.getInstance()` calls in `runCatching { … }` so the
 * manager can NEVER crash the app — privacy-stronger fallbacks degrade to
 * "cookies wiped" rather than "cookies leaked."
 */
interface CookieJarSnapshotManager {

    /**
     * Capture a snapshot of cookies currently held for [origins]. If a
     * snapshot already exists from a prior call, this method is a NO-OP
     * (the existing snapshot is preserved — guarantees a `0 → 1 → 2 → 1`
     * incognito-tab sequence does not re-capture in the middle).
     *
     * Held entirely in memory inside the manager.
     */
    suspend fun captureSnapshot(origins: List<String>)

    /**
     * Restore the previously captured snapshot. Wipes the live cookie jar
     * (incl. any cookies set during the incognito session) and writes the
     * snapshot back. If no snapshot is held, this is a NO-OP (defensive —
     * does NOT wipe cookies).
     *
     * After successful restore the held snapshot is wiped so the next
     * [captureSnapshot] will succeed.
     */
    suspend fun restoreSnapshot()

    /** For diagnostic / unit-testing. */
    fun hasSnapshot(): Boolean

    /**
     * Spec 016 FR-030 — discard the normal-browsing cookies set aside for restoring at the end
     * of the current incognito session, so that closing the last incognito tab restores nothing.
     *
     * Contract:
     *  - If a snapshot is held, REPLACE it with `CookieJarSnapshot.EMPTY`. Do NOT clear it to
     *    null: [restoreSnapshot] returns early when nothing is held and would then skip its
     *    wipe, leaking cookies set during the incognito session into normal browsing
     *    (data-model §5, research R7).
     *  - If no snapshot is held (no incognito session), this is a no-op.
     *  - Serialised with capture and restore under the manager's existing mutex.
     *  - Never throws; there is no platform call to fail.
     *
     * Callers MUST invoke this BEFORE wiping the cookie jar, never after (research R7).
     */
    suspend fun discardSetAsideCookies()
}
