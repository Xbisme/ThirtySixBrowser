// Spec 016 contract — additions to two interfaces shipped by earlier specs.
// Each interface keeps every existing member unchanged; only the members below are new.

// ─────────────────────────────────────────────────────────────────────────────
// 1. CookieJarSnapshotManager (Spec 012)
// Target: app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/CookieJarSnapshotManager.kt
// Implementors to update: CookieJarSnapshotManagerImpl (main),
//                         FakeCookieJarSnapshotManager (test/testdoubles)
// ─────────────────────────────────────────────────────────────────────────────

package com.raumanian.thirtysix.browser.domain.repository

interface CookieJarSnapshotManager {

    // …existing: captureSnapshot(origins), restoreSnapshot(), hasSnapshot() — unchanged…

    /**
     * Spec 016 FR-030 — discard the normal-browsing cookies set aside for restoring at the end
     * of the current incognito session, so that closing the last incognito tab restores nothing.
     *
     * Contract:
     *  - If a snapshot is held, REPLACE it with `CookieJarSnapshot.EMPTY`. Do NOT clear it to
     *    null: `restoreSnapshot()` returns early when nothing is held and would then skip its
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

// ─────────────────────────────────────────────────────────────────────────────
// 2. FaviconCache (Spec 011)
// Target: app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/cache/FaviconCache.kt
// Implementors to update: DiskFaviconCache (main) plus every no-op / recording double in
//                         app/src/test and app/src/androidTest (enumerated in plan.md)
// ─────────────────────────────────────────────────────────────────────────────

interface FaviconCache {

    // …existing: version, save(url, bitmap), fileFor(url) — unchanged…

    /**
     * Spec 016 FR-029 — delete every cached site icon.
     *
     * Contract (mirrors `ScreenshotCache.clearAll`):
     *  - Runs on the IO dispatcher. Idempotent; a missing directory is not an error.
     *  - Bumps [version] afterwards, so every composable reading an icon re-evaluates and
     *    falls back to its placeholder until the icon is fetched again.
     *  - A file that cannot be deleted is logged and skipped rather than aborting the rest;
     *    the call returns normally, and the caller treats a thrown exception — not a partial
     *    deletion — as the category's failure signal.
     */
    suspend fun clearAll()
}
