# Contract: `IncognitoTabRepository`

**Location**: [`domain/repository/IncognitoTabRepository.kt`](../../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/IncognitoTabRepository.kt) (to be created)
**Implementation**: [`data/repository/IncognitoTabRepositoryImpl.kt`](../../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/IncognitoTabRepositoryImpl.kt) (to be created)
**Hilt scope**: `@Singleton` (per [research.md R4](../research.md#r4--hilt-scoping-for-incognitotabrepository-and-cookiejarsnapshotmanager))

## Interface

```kotlin
interface IncognitoTabRepository {

    /**
     * Hot Flow of every currently-open incognito tab. Sorted by
     * lastActiveAt DESC, then id ASC (matches the Spec 011 ordering rule).
     *
     * Emits an empty list at app launch and after the last incognito tab
     * is closed.
     *
     * Process death wipes all state — the next subscription after cold
     * start sees an empty list. This is intentional and satisfies FR-012.
     */
    fun observeTabs(): Flow<List<Tab>>

    /**
     * Open a new incognito tab pointing to [url]. The new tab is
     * activated immediately (its lastActiveAt is set to now()).
     *
     * Side effects (atomic with the tab creation, mutex-guarded):
     *  1. If this is the FIRST incognito tab in the current session
     *     (transition 0 → 1), the global cookie jar is captured into a
     *     snapshot via CookieJarSnapshotManager.captureSnapshot before
     *     this method returns.
     *  2. The new Tab is appended to the in-memory state with
     *     isIncognito = true and a NEGATIVE Long id.
     *
     * Cap-reached failures arrive as
     * Result.Error(throwable = MaxIncognitoTabsReachedException, ...)
     * so callers can disambiguate from the normal-tab cap (per FR-004).
     */
    suspend fun createTab(url: String): Result<Tab>

    /**
     * Make [tabId] the active incognito tab. Writes
     * lastActiveAt = now() on the matching entry. No-op if [tabId]
     * does not exist in the incognito state.
     *
     * NOTE: This method does NOT touch TabRepository — the merged
     * active-tab pointer is computed at the use-case layer
     * (ObserveAllTabsUseCase) from the merged Flow head.
     */
    suspend fun switchActiveTab(tabId: Long)

    /**
     * Update the live URL + title of an incognito tab in the in-memory
     * state. Called by UpdateActiveTabUrlAndTitleUseCase when the
     * active tab is incognito (instead of writing to TabRepository).
     *
     * No-op if [tabId] does not exist.
     */
    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)

    /**
     * Close [tabId]. Mutex-guarded.
     *
     * Side effects:
     *  1. Tab is removed from the in-memory state.
     *  2. If the post-close incognito count would drop to 0
     *     (transition 1 → 0), the cookie jar snapshot is restored
     *     via CookieJarSnapshotManager.restoreSnapshot before this
     *     method returns.
     *
     * No-op if [tabId] does not exist.
     */
    suspend fun closeTab(tabId: Long)

    /**
     * Close every incognito tab. Mutex-guarded; atomic.
     *
     * Side effects:
     *  1. Wipe the in-memory state to empty.
     *  2. Restore the cookie jar snapshot exactly once (idempotent
     *     with respect to repeated closeAll calls — restoreSnapshot
     *     is a no-op if no snapshot is held).
     */
    suspend fun closeAll()

    /**
     * Snapshot of the current incognito tab count. Used by
     * CreateIncognitoTabUseCase pre-flight checks (FR-004) and by
     * TabsViewModel for affordance visibility logic (US5 #1/#2).
     */
    suspend fun getCount(): Int
}

class MaxIncognitoTabsReachedException : Exception("Maximum number of incognito tabs reached")
```

## Concurrency guarantees

- All mutating methods (`createTab`, `closeTab`, `closeAll`, `switchActiveTab`, `updateTabUrlAndTitle`) acquire the same internal `Mutex`. Concurrent calls are serialized.
- `observeTabs()` returns a `MutableStateFlow.asStateFlow()` view; collectors see consistent snapshots (no torn state).
- `getCount()` reads from `state.value` after acquiring the mutex (consistent with create/close serialization).

## Test contract (unit)

The following tests live in `app/src/test/.../data/repository/IncognitoTabRepositoryImplTest.kt`:

| # | Test | Verifies |
|---|------|----------|
| 1 | `observeTabs starts empty` | Initial state at construction time |
| 2 | `createTab inserts tab with isIncognito=true and negative id` | Invariants 2 + 3 |
| 3 | `createTab triggers snapshot capture only on 0→1 transition` | Snapshot lifecycle (mock CookieJarSnapshotManager, assert `captureSnapshot` invoked exactly once across multiple creates) |
| 4 | `closeTab triggers snapshot restore only on 1→0 transition` | Snapshot lifecycle (mock manager, assert `restoreSnapshot` invoked exactly once) |
| 5 | `closeAll wipes state and restores snapshot` | Atomicity |
| 6 | `createTab over MAX_INCOGNITO_TABS returns Error(MaxIncognitoTabsReachedException)` | FR-004 cap enforcement |
| 7 | `IDs are unique and strictly decreasing` | R3 invariant |
| 8 | `concurrent createTab+closeTab serialize via mutex` | No torn state under stress (uses `runTest` with multiple launches) |
| 9 | `closeTab on unknown tabId is no-op` | Defensive read |
| 10 | `updateTabUrlAndTitle modifies in-memory state without touching TabRepository` | Layer boundary (mock TabRepository, assert no calls) |

## Test contract (instrumented)

`app/src/androidTest/.../IncognitoStressInstrumentedTest.kt`:

- 100-cycle open/close stress (per R8 / SC-005) — passes if no exception propagated to test runner.
- Heap delta sanity check < 2 MB.
