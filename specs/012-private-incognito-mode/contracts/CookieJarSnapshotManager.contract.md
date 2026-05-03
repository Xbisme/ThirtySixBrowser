# Contract: `CookieJarSnapshotManager`

**Interface location**: [`domain/repository/CookieJarSnapshotManager.kt`](../../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/CookieJarSnapshotManager.kt) (to be created — pure Kotlin interface, kept in `domain/repository/` despite the "Repository" naming because the Hilt convention places singleton platform-state managers next to repository interfaces)
**Implementation**: [`data/local/cookies/CookieJarSnapshotManagerImpl.kt`](../../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/cookies/CookieJarSnapshotManagerImpl.kt) (to be created — wraps platform `android.webkit.CookieManager`)
**Hilt scope**: `@Singleton` (per [research.md R4](../research.md#r4--hilt-scoping-for-incognitotabrepository-and-cookiejarsnapshotmanager))

## Interface

```kotlin
interface CookieJarSnapshotManager {

    /**
     * Capture a snapshot of the cookies currently held for [origins].
     * Held in memory inside the manager (not exposed externally).
     *
     * Behaviour:
     *  - For each origin, calls CookieManager.getCookie(origin) and
     *    stores the returned header in a Map<origin, header>.
     *  - Calls CookieManager.flush() once before iterating to ensure
     *    the captured state reflects all in-flight writes.
     *  - If a snapshot already exists from a prior call, this method
     *    is a NO-OP (idempotent — the existing snapshot is preserved).
     *    This guarantees that a 0→1→2→1 incognito-tab sequence does
     *    NOT re-capture in the middle.
     *
     * Caller MUST invoke this BEFORE the first incognito tab
     * lifecycle event that could mutate the global cookie jar.
     *
     * Failure modes (all non-throwing, never crash):
     *  - CookieManager.getCookie throws → caught, that origin's entry
     *    is silently omitted from the snapshot. Logged at WARN.
     *  - Empty origin list → captures an empty snapshot; restore is
     *    still a valid wipe.
     */
    suspend fun captureSnapshot(origins: List<String>)

    /**
     * Restore the previously captured snapshot.
     *
     * Behaviour:
     *  1. If no snapshot is held, this is a NO-OP (defensive — does
     *     NOT wipe cookies; protects against concurrent mis-sequencing).
     *  2. Otherwise:
     *     a. CookieManager.removeAllCookies (suspending wrapper around
     *        the callback API).
     *     b. CookieManager.flush().
     *     c. For each (origin, header) in the snapshot, split header
     *        on "; " and call setCookie(origin, eachPair) once per
     *        name=value pair.
     *     d. CookieManager.flush() again to persist the restored state.
     *     e. Wipe the held snapshot (set to null) so the next
     *        captureSnapshot will succeed.
     *
     * Caller MUST invoke this AFTER the last incognito tab is removed
     * from the in-memory state, so any incognito-set cookies are
     * captured by removeAllCookies before restore writes the original
     * cookies back.
     *
     * Failure modes (all non-throwing, never crash):
     *  - removeAllCookies callback fails → still wipes snapshot;
     *    privacy-stronger fallback (cookies are gone, not leaked).
     *  - setCookie throws on a malformed pair → that pair is skipped,
     *    others continue. Logged at WARN.
     */
    suspend fun restoreSnapshot()

    /**
     * For diagnostic / unit-testing.
     */
    fun hasSnapshot(): Boolean
}
```

## Concurrency guarantees

- Both `captureSnapshot` and `restoreSnapshot` acquire the same internal `Mutex`. Sequential by construction.
- `IncognitoTabRepositoryImpl` calls these methods inside its own mutex-guarded sections (per FR-011a step 4 — "snapshot capture and restore MUST be serialized"), so the manager's mutex is a defence-in-depth rather than the primary serialization point.
- The `CookieManager` itself is thread-safe per Android docs; the manager simply ensures our snapshot data structure is consistent.

## Crash-safety guarantees

- **Never throws**. Every platform call (`getCookie`, `setCookie`, `removeAllCookies`, `flush`) is wrapped in `runCatching { … }` with a logged WARN on failure and graceful continue.
- **Idempotent**. Calling `captureSnapshot` twice in a row preserves the first snapshot. Calling `restoreSnapshot` when no snapshot is held is a no-op. Both protect against lifecycle-event reordering.
- **Bounded memory**. The snapshot is at most `MAX_TABS = 50` origins × ~2 KB per cookie header ≈ 100 KB. Released entirely on restore.
- **Process death = lost snapshot = correct**: incognito tabs are also lost (per FR-012), so there's no live incognito session to preserve cookies for. Normal-tab cookies survived in the cookie store anyway (they were only snapshotted, never wiped, until restore would have run).

## Test contract (unit)

The following tests live in `app/src/test/.../data/local/cookies/CookieJarSnapshotManagerImplTest.kt` (uses Robolectric SDK 33 to provide `CookieManager`):

| # | Test | Verifies |
|---|------|----------|
| 1 | `captureSnapshot stores cookies for given origins` | Happy path |
| 2 | `captureSnapshot is no-op when snapshot already exists` | Idempotency guarantee 1 |
| 3 | `restoreSnapshot is no-op when no snapshot held` | Idempotency guarantee 2 |
| 4 | `restoreSnapshot wipes snapshot after restoring` | Lifecycle correctness |
| 5 | `restoreSnapshot wipes ALL cookies (incl. incognito-set) before restoring` | Privacy guarantee |
| 6 | `captureSnapshot continues on per-origin getCookie failure` | Crash-safety |
| 7 | `restoreSnapshot continues on per-pair setCookie failure` | Crash-safety |
| 8 | `hasSnapshot returns true after capture, false after restore` | Diagnostic correctness |

## Test contract (instrumented)

`app/src/androidTest/.../CookieRestoreInstrumentedTest.kt`:

- Set a known cookie on origin A in a normal WebView, capture snapshot, set a known cookie on origin A in an incognito WebView, restore, assert origin A has only the original normal-tab cookie (incognito-set cookie is gone).
