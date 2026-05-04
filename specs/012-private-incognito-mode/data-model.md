# Data Model: Private / Incognito Mode

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)
**Date**: 2026-05-03

> Documents the entity / shape changes introduced by this feature. **Zero Room schema migration** — `TabEntity` v1 from Spec 005 is unchanged. All new state lives in memory.

## Entity 1 — `Tab` (domain model, MODIFIED)

**Location**: [`domain/model/Tab.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Tab.kt)

**Change**: add field `isIncognito: Boolean` with default `false`.

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | **Positive** for normal tabs (Room PK auto-increment, ≥ 1). **Negative** for incognito (`IncognitoTabRepositoryImpl.nextId()`, ≤ -1). Disjoint key spaces guaranteed (R3). |
| `url` | `String` | Unchanged from Spec 011. |
| `title` | `String` | Unchanged from Spec 011. |
| `position` | `Int` | Unchanged for normal tabs; for incognito tabs, treated as a tiebreaker only (incognito tabs sort by `lastActiveAt DESC` first). |
| `createdAt` | `Long` | Epoch millis. For incognito, `System.currentTimeMillis()` at create. |
| `lastActiveAt` | `Long` | Epoch millis. Drives merged ordering rule (R9). |
| `isIncognito` | `Boolean` | **NEW**. Default `false` so existing test fixtures and Room-mapped instances don't need to be re-written. |

**Mapper change** ([`data/mapper/TabMapper.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapper.kt)):
`TabEntity.toDomain()` MUST always emit `isIncognito = false` — Room never stores incognito state, so any tab loaded from Room is by definition normal.

There is **no `Tab.toEntity()`** for incognito tabs — the in-memory repository writes directly to its own state, bypassing Room.

## Entity 2 — `IncognitoTabRepository` state (NEW, in-memory only)

**Location**: [`data/repository/IncognitoTabRepositoryImpl.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/IncognitoTabRepositoryImpl.kt) (to be created)

```kotlin
@Singleton
class IncognitoTabRepositoryImpl @Inject constructor(
    private val cookieJarSnapshotManager: CookieJarSnapshotManager, // for snapshot-on-first-create
    private val tabRepository: TabRepository,                       // to enumerate origins for snapshot
    private val dispatchers: DispatcherProvider,
) : IncognitoTabRepository {
    private val state = MutableStateFlow<List<Tab>>(emptyList())
    private val nextId = AtomicLong(-1L)
    private val mutex = Mutex()
    // ...
}
```

**Invariants**:

1. `state.value.size ≤ BrowserLimits.MAX_INCOGNITO_TABS` (50, enforced inside `mutex.withLock { … }` in `create`).
2. Every entry has `isIncognito = true`.
3. Every entry has `id ≤ -1` (negative half-plane).
4. `state` Flow re-emits on every mutation.
5. **Cookie snapshot lifecycle is bound to `state.size` transitions**:
   - On `0 → 1` (first incognito tab created): call `cookieJarSnapshotManager.captureSnapshot(originsFromTabRepository)` BEFORE returning the new tab to the caller.
   - On `1 → 0` (last incognito tab closed): call `cookieJarSnapshotManager.restoreSnapshot()` AFTER removing the tab from state but BEFORE the close-tab method returns.
6. `closeAll()` is a single atomic operation (mutex-guarded): wipes `state`, then triggers cookie restore exactly once.

**Process death**: `state` is a `MutableStateFlow` inside a `@Singleton` — destroyed when the process is killed. On cold start, `state.value = emptyList()` per the field initializer. No defensive read-from-disk needed; FR-012 is satisfied by language semantics.

## Entity 3 — `CookieJarSnapshot` (NEW, in-memory data class)

**Location**: [`data/local/cookies/CookieJarSnapshot.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/cookies/CookieJarSnapshot.kt) (to be created)

```kotlin
data class CookieJarSnapshot(
    val capturedAt: Long,                       // epoch millis, for diagnostic only
    val entries: Map<String, String>,           // origin → "name=value; name2=value2 …" header
)
```

| Field | Type | Notes |
|-------|------|-------|
| `capturedAt` | `Long` | For diagnostic logs only — never compared, never used for invalidation. |
| `entries` | `Map<String, String>` | Keys are origins (e.g. `https://example.com`), values are the concatenated `Cookie:` header that `CookieManager.getCookie(origin)` returned. **Cookie attributes (Domain, Path, Expires, Secure, HttpOnly, SameSite) are NOT preserved** — see R1. |

**Lifecycle**:

- Created by `CookieJarSnapshotManager.captureSnapshot(origins)` inside the `0 → 1` transition.
- Held inside `CookieJarSnapshotManager` private state (not exposed externally).
- Wiped + replaced with `null` after `restoreSnapshot()` completes.
- Held in memory only — no persistence (process death = lost snapshot, which is correct because incognito tabs are also lost).

## Entity 4 — `BrowserLimits.MAX_INCOGNITO_TABS` (NEW constant)

**Location**: [`core/constants/BrowserLimits.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt)

```kotlin
object BrowserLimits {
    const val MAX_TABS: Int = 50              // existing, unchanged
    const val MAX_INCOGNITO_TABS: Int = 50    // NEW — independent cap per Q1
}
```

**Rationale**: Independent cap per Q1 clarification. Default value matches `MAX_TABS` for symmetry; can be tuned independently (e.g., reduced to 20 if memory pressure surfaces in stress tests).

## Entity 5 — `BrowserUiState.isIncognito` (MODIFIED)

**Location**: [`presentation/browser/BrowserUiState.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt)

**Change**: add field `val isIncognito: Boolean = false`.

**Source**: derived from `Tab.isIncognito` of the active tab. Updated whenever the active-tab pointer changes (via the existing observer wired in `BrowserViewModel`).

**Consumers**:

- `BrowserScreen` — render the incognito indicator beside the address bar (FR-015).
- `BrowserWebView` — apply the incognito-specific `WebSettings` lockdown (R5).
- `BrowserViewModel.onIconReceived` / `onScreenshotReady` — short-circuit cache writes (R7).
- `MainActivity` — drive `SecureWindowEffect` for FLAG_SECURE binding (R2).

## Entity 6 — `TabsUiState.incognitoTabCount` (MODIFIED)

**Location**: [`presentation/tabs/TabsUiState.kt`](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsUiState.kt)

**Change**: add field `val incognitoTabCount: Int = 0`.

**Source**: derived from the merged tab list (`tabs.count { it.isIncognito }`).

**Consumers**:

- `TabsScreen` — show/hide the "Close all incognito" affordance (visible iff `incognitoTabCount > 0`).
- `TabsScreen` — drive the count badge on the new-incognito-tab affordance (optional UX detail; deferred to UI polish if not needed).

## Merged ordering rule for `ObserveAllTabsUseCase`

The new `ObserveAllTabsUseCase` returns `Flow<List<Tab>>` that combines `TabRepository.observeTabs()` and `IncognitoTabRepository.observeTabs()` and sorts the union by:

1. `lastActiveAt DESC` (most recently active first — drives switcher ordering)
2. `isIncognito ASC` (when timestamps tie, normal tabs precede incognito; harmless tiebreaker)
3. `id ASC` (final tiebreaker — guarantees stable, deterministic order)

**Rationale**: identical primary sort to Spec 011 (`Tab::lastActiveAt DESC then Tab::id ASC`); the `isIncognito ASC` middle key is a deterministic tiebreaker for the rare case where a normal tab close + incognito tab create happen in the same `currentTimeMillis()` instant. The active-tab pointer (`merged.firstOrNull()`) follows the head of this sort, satisfying R9.

## State transitions — incognito session lifecycle

```
                  ┌──────────────┐
                  │   no incog   │
                  │   tabs       │  ← state at app launch + after last-close
                  └─────┬────────┘
                        │ createIncognitoTab()
                        │ (mutex-guarded)
                        │ 1. captureSnapshot(origins from TabRepository)
                        │ 2. add Tab(isIncognito=true) to state
                        │ 3. emit new merged Flow
                        ▼
                  ┌──────────────┐
                  │ ≥ 1 incog    │
                  │ tab(s)       │
                  └─────┬────────┘
        ┌───────────────┼───────────────┐
        │               │               │
        │ create more   │ close one     │ closeAll()
        │ (no snapshot  │ (no restore   │  - wipe state
        │  re-capture)  │  if size > 1) │  - restoreSnapshot()
        │               │               │  - emit
        ▼               ▼               ▼
  same state     ┌────────────┐   ┌──────────────┐
                 │ size = 0?  │   │   no incog   │
                 │  yes  no   │   │   tabs       │
                 └──┬─────┴───┘   └──────────────┘
              size == 0:
              1. restoreSnapshot()
              2. emit empty incog list
              3. merged Flow's new head is most-recent
                 normal tab (per R9)
```

**Crash-safety claim**: every state transition is guarded by `IncognitoTabRepositoryImpl`'s internal `Mutex`. Concurrent `create` + `close` calls serialize, so the cookie snapshot capture/restore protocol cannot race into an inconsistent state (e.g., capturing twice or restoring before capturing).
